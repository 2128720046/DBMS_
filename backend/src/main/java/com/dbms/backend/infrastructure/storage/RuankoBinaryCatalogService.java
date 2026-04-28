package com.dbms.backend.infrastructure.storage;

import com.dbms.backend.dto.ColumnDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RuankoBinaryCatalogService {

    private static final int NAME_SIZE = 128;
    private static final int PATH_SIZE = 256;

    private final Path dbmsRoot;
    private final Path dataRoot;
    private final Path catalogFile;

    public RuankoBinaryCatalogService(@Value("${dbms.storage.root:.}") String rootPath) {
        this.dbmsRoot = Path.of(rootPath).toAbsolutePath().normalize();
        this.dataRoot = dbmsRoot.resolve("data");
        this.catalogFile = dbmsRoot.resolve("ruanko.db");
    }

    public synchronized void createDatabaseArtifacts(String databaseName) {
        long now = Instant.now().toEpochMilli();
        try {
            Files.createDirectories(dataRoot);
            Path dbDir = dataRoot.resolve(databaseName);
            Files.createDirectories(dbDir);

            Path tbFile = dbDir.resolve(databaseName + ".tb");
            Path logFile = dbDir.resolve(databaseName + ".log");
            createBinaryListFile(tbFile);
            if (!Files.exists(logFile)) {
                Files.createFile(logFile);
            }

            List<DatabaseBlock> blocks = readDatabaseBlocks();
            DatabaseBlock existing = blocks.stream()
                    .filter(block -> block.name.equalsIgnoreCase(databaseName))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                blocks.add(new DatabaseBlock(databaseName, false, dbDir.toString(), now));
            } else {
                existing.filename = dbDir.toString();
            }
            writeDatabaseBlocks(blocks);
        } catch (IOException ex) {
            throw new IllegalStateException("创建数据库二进制文件结构失败: " + ex.getMessage(), ex);
        }
    }

    public synchronized void dropDatabaseArtifacts(String databaseName) {
        try {
            List<DatabaseBlock> blocks = readDatabaseBlocks();
            blocks.removeIf(block -> block.name.equalsIgnoreCase(databaseName));
            writeDatabaseBlocks(blocks);

            Path dbDir = dataRoot.resolve(databaseName);
            if (Files.exists(dbDir)) {
                try (var walk = Files.walk(dbDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("删除数据库文件失败: " + path, ex);
                        }
                    });
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("删除数据库二进制文件结构失败: " + ex.getMessage(), ex);
        }
    }

    public synchronized void createTableArtifacts(String databaseName, String tableName, List<ColumnDefinition> columns) {
        long now = Instant.now().toEpochMilli();
        try {
            Path dbDir = ensureDatabaseDir(databaseName);
            Path tdf = dbDir.resolve(tableName + ".tdf");
            Path trd = dbDir.resolve(tableName + ".trd");
            Path tic = dbDir.resolve(tableName + ".tic");
            Path tid = dbDir.resolve(tableName + ".tid");

            writeFieldDefinitions(tdf, columns, now);
            writeConstraintDefinitions(tic, columns);
            createBinaryListFile(tid);
            if (!Files.exists(trd)) {
                Files.createFile(trd);
            }

            upsertTableBlock(databaseName, new TableBlock(
                    tableName,
                    0,
                    columns == null ? 0 : columns.size(),
                    tdf.toString(),
                    tic.toString(),
                    trd.toString(),
                    tid.toString(),
                    now,
                    now
            ));
        } catch (IOException ex) {
            throw new IllegalStateException("创建表二进制文件结构失败: " + ex.getMessage(), ex);
        }
    }

    public synchronized void updateTableArtifacts(String databaseName, String tableName, List<ColumnDefinition> columns) {
        long now = Instant.now().toEpochMilli();
        try {
            Path dbDir = ensureDatabaseDir(databaseName);
            Path tdf = dbDir.resolve(tableName + ".tdf");
            Path tic = dbDir.resolve(tableName + ".tic");
            writeFieldDefinitions(tdf, columns, now);
            writeConstraintDefinitions(tic, columns);

            List<TableBlock> blocks = readTableBlocks(databaseName);
            for (TableBlock block : blocks) {
                if (block.name.equalsIgnoreCase(tableName)) {
                    block.fieldNum = columns == null ? 0 : columns.size();
                    block.mtime = now;
                }
            }
            writeTableBlocks(databaseName, blocks);
        } catch (IOException ex) {
            throw new IllegalStateException("更新表二进制文件结构失败: " + ex.getMessage(), ex);
        }
    }

    public synchronized void dropTableArtifacts(String databaseName, String tableName) {
        try {
            Path dbDir = ensureDatabaseDir(databaseName);
            Files.deleteIfExists(dbDir.resolve(tableName + ".tdf"));
            Files.deleteIfExists(dbDir.resolve(tableName + ".trd"));
            Files.deleteIfExists(dbDir.resolve(tableName + ".tic"));
            Files.deleteIfExists(dbDir.resolve(tableName + ".tid"));

            List<TableBlock> blocks = readTableBlocks(databaseName);
            blocks.removeIf(block -> block.name.equalsIgnoreCase(tableName));
            writeTableBlocks(databaseName, blocks);
        } catch (IOException ex) {
            throw new IllegalStateException("删除表二进制文件结构失败: " + ex.getMessage(), ex);
        }
    }

    private void upsertTableBlock(String databaseName, TableBlock tableBlock) throws IOException {
        List<TableBlock> blocks = readTableBlocks(databaseName);
        TableBlock existing = blocks.stream().filter(block -> block.name.equalsIgnoreCase(tableBlock.name)).findFirst().orElse(null);
        if (existing == null) {
            blocks.add(tableBlock);
        } else {
            existing.recordNum = tableBlock.recordNum;
            existing.fieldNum = tableBlock.fieldNum;
            existing.tdf = tableBlock.tdf;
            existing.tic = tableBlock.tic;
            existing.trd = tableBlock.trd;
            existing.tid = tableBlock.tid;
            existing.mtime = tableBlock.mtime;
        }
        writeTableBlocks(databaseName, blocks);
    }

    private Path ensureDatabaseDir(String databaseName) throws IOException {
        Path dbDir = dataRoot.resolve(databaseName);
        Files.createDirectories(dbDir);
        Path tbFile = dbDir.resolve(databaseName + ".tb");
        createBinaryListFile(tbFile);
        return dbDir;
    }

    private void createBinaryListFile(Path file) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Files.createDirectories(file.getParent());
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(0);
        }
    }

    private List<DatabaseBlock> readDatabaseBlocks() throws IOException {
        createBinaryListFile(catalogFile);
        List<DatabaseBlock> blocks = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(catalogFile)))) {
            int count = safeReadCount(input);
            for (int i = 0; i < count; i++) {
                DatabaseBlock block = new DatabaseBlock();
                block.name = readFixedString(input, NAME_SIZE);
                block.type = input.readBoolean();
                input.skipBytes(3);
                block.filename = readFixedString(input, PATH_SIZE);
                block.crtime = input.readLong();
                blocks.add(block);
            }
        }
        return blocks;
    }

    private void writeDatabaseBlocks(List<DatabaseBlock> blocks) throws IOException {
        Files.createDirectories(catalogFile.getParent());
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(catalogFile)))) {
            output.writeInt(blocks.size());
            for (DatabaseBlock block : blocks) {
                writeFixedString(output, block.name, NAME_SIZE);
                output.writeBoolean(block.type);
                output.write(new byte[3]);
                writeFixedString(output, block.filename, PATH_SIZE);
                output.writeLong(block.crtime);
            }
        }
    }

    private List<TableBlock> readTableBlocks(String databaseName) throws IOException {
        Path tbFile = ensureDatabaseDir(databaseName).resolve(databaseName + ".tb");
        List<TableBlock> blocks = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(tbFile)))) {
            int count = safeReadCount(input);
            for (int i = 0; i < count; i++) {
                TableBlock block = new TableBlock();
                block.name = readFixedString(input, NAME_SIZE);
                block.recordNum = input.readInt();
                block.fieldNum = input.readInt();
                block.tdf = readFixedString(input, PATH_SIZE);
                block.tic = readFixedString(input, PATH_SIZE);
                block.trd = readFixedString(input, PATH_SIZE);
                block.tid = readFixedString(input, PATH_SIZE);
                block.crtime = input.readLong();
                block.mtime = input.readLong();
                blocks.add(block);
            }
        }
        return blocks;
    }

    private void writeTableBlocks(String databaseName, List<TableBlock> blocks) throws IOException {
        Path tbFile = ensureDatabaseDir(databaseName).resolve(databaseName + ".tb");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tbFile)))) {
            output.writeInt(blocks.size());
            for (TableBlock block : blocks) {
                writeFixedString(output, block.name, NAME_SIZE);
                output.writeInt(block.recordNum);
                output.writeInt(block.fieldNum);
                writeFixedString(output, block.tdf, PATH_SIZE);
                writeFixedString(output, block.tic, PATH_SIZE);
                writeFixedString(output, block.trd, PATH_SIZE);
                writeFixedString(output, block.tid, PATH_SIZE);
                output.writeLong(block.crtime);
                output.writeLong(block.mtime);
            }
        }
    }

    private void writeFieldDefinitions(Path tdfFile, List<ColumnDefinition> columns, long now) throws IOException {
        List<ColumnDefinition> safeColumns = columns == null ? List.of() : columns;
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tdfFile)))) {
            output.writeInt(safeColumns.size());
            for (int i = 0; i < safeColumns.size(); i++) {
                ColumnDefinition column = safeColumns.get(i);
                output.writeInt(i + 1);
                writeFixedString(output, column.getName(), NAME_SIZE);
                output.writeInt(mapFieldType(column.getType()));
                output.writeInt(extractTypeParam(column));
                output.writeLong(now);
                output.writeInt(buildIntegrityMask(column));
            }
        }
    }

    private void writeConstraintDefinitions(Path ticFile, List<ColumnDefinition> columns) throws IOException {
        List<ConstraintBlock> constraints = new ArrayList<>();
        if (columns != null) {
            for (ColumnDefinition column : columns) {
                if (Boolean.TRUE.equals(column.getPk())) {
                    constraints.add(new ConstraintBlock("PK_" + column.getName(), column.getName(), 1, "PRIMARY KEY"));
                }
                if (Boolean.TRUE.equals(column.getUq())) {
                    constraints.add(new ConstraintBlock("UQ_" + column.getName(), column.getName(), 2, "UNIQUE"));
                }
                if (Boolean.FALSE.equals(column.getNullable())) {
                    constraints.add(new ConstraintBlock("NN_" + column.getName(), column.getName(), 3, "NOT NULL"));
                }
            }
        }
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(ticFile)))) {
            output.writeInt(constraints.size());
            for (ConstraintBlock constraint : constraints) {
                writeFixedString(output, constraint.name, NAME_SIZE);
                writeFixedString(output, constraint.field, NAME_SIZE);
                output.writeInt(constraint.type);
                writeFixedString(output, constraint.param, PATH_SIZE);
            }
        }
    }

    private int mapFieldType(String typeText) {
        if (typeText == null) {
            return 0;
        }
        String normalized = typeText.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("INT")) {
            return 1;
        }
        if (normalized.startsWith("BIGINT")) {
            return 2;
        }
        if (normalized.startsWith("VARCHAR")) {
            return 3;
        }
        if (normalized.startsWith("CHAR")) {
            return 4;
        }
        if (normalized.startsWith("DATE")) {
            return 5;
        }
        if (normalized.startsWith("DATETIME") || normalized.startsWith("TIMESTAMP")) {
            return 6;
        }
        if (normalized.startsWith("DECIMAL")) {
            return 7;
        }
        if (normalized.startsWith("TEXT")) {
            return 8;
        }
        return 0;
    }

    private int extractTypeParam(ColumnDefinition column) {
        if (column.getLength() != null && column.getLength() > 0) {
            return column.getLength();
        }
        String type = column.getType();
        if (type == null) {
            return 0;
        }
        int left = type.indexOf('(');
        int right = type.indexOf(')');
        if (left < 0 || right <= left + 1) {
            return 0;
        }
        String value = type.substring(left + 1, right).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int buildIntegrityMask(ColumnDefinition column) {
        int mask = 0;
        if (Boolean.TRUE.equals(column.getPk())) {
            mask |= 1;
        }
        if (Boolean.TRUE.equals(column.getUq())) {
            mask |= 1 << 1;
        }
        if (Boolean.FALSE.equals(column.getNullable())) {
            mask |= 1 << 2;
        }
        return mask;
    }

    private int safeReadCount(DataInputStream input) throws IOException {
        if (input.available() <= 0) {
            return 0;
        }
        return Math.max(input.readInt(), 0);
    }

    private String readFixedString(DataInputStream input, int size) throws IOException {
        byte[] buffer = input.readNBytes(size);
        int end = 0;
        while (end < buffer.length && buffer[end] != 0) {
            end++;
        }
        return new String(buffer, 0, end, StandardCharsets.UTF_8);
    }

    private void writeFixedString(DataOutputStream output, String value, int size) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        byte[] fixed = new byte[size];
        System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, size));
        output.write(fixed);
    }

    private static final class DatabaseBlock {
        private String name;
        private boolean type;
        private String filename;
        private long crtime;

        private DatabaseBlock() {
        }

        private DatabaseBlock(String name, boolean type, String filename, long crtime) {
            this.name = name;
            this.type = type;
            this.filename = filename;
            this.crtime = crtime;
        }
    }

    private static final class TableBlock {
        private String name;
        private int recordNum;
        private int fieldNum;
        private String tdf;
        private String tic;
        private String trd;
        private String tid;
        private long crtime;
        private long mtime;

        private TableBlock() {
        }

        private TableBlock(String name,
                           int recordNum,
                           int fieldNum,
                           String tdf,
                           String tic,
                           String trd,
                           String tid,
                           long crtime,
                           long mtime) {
            this.name = name;
            this.recordNum = recordNum;
            this.fieldNum = fieldNum;
            this.tdf = tdf;
            this.tic = tic;
            this.trd = trd;
            this.tid = tid;
            this.crtime = crtime;
            this.mtime = mtime;
        }
    }

    private record ConstraintBlock(String name, String field, int type, String param) {
    }
}