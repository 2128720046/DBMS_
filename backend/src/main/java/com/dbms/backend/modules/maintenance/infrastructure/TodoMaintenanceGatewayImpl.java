package com.dbms.backend.modules.maintenance.infrastructure;

import com.dbms.backend.modules.maintenance.domain.MaintenanceGateway;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.io.BinaryIoUtils;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 数据库维护网关占位实现。
 */
@Repository
public class TodoMaintenanceGatewayImpl implements MaintenanceGateway {

    private static final int DB_BLOCK_SIZE = 401;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    @Override
    public List<Map<String, Object>> listBackups(String schemaName) {
        Path schemaBackupDir = backupDir(schemaName);
        if (!Files.exists(schemaBackupDir)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            try (var stream = Files.list(schemaBackupDir)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".bak"))
                        .sorted((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .forEach(p -> {
                            try {
                                Map<String, Object> row = new HashMap<>();
                                row.put("name", p.getFileName().toString());
                                row.put("size", Files.size(p));
                                row.put("updatedAt", Files.getLastModifiedTime(p).toString());
                                row.put("desc", "");
                                results.add(row);
                            } catch (IOException ignored) {
                            }
                        });
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("读取备份列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Path backupSchema(String schemaName, Path targetDir) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        if (!schemaDir.exists()) {
            throw new IllegalArgumentException("数据库不存在: " + schemaName);
        }

        Path backupFile = resolveBackupTarget(schemaName, targetDir);
        try {
            Files.createDirectories(backupFile.getParent());
            writeZip(schemaDir.toPath(), backupFile, schemaName);
        } catch (IOException e) {
            throw new RuntimeException("备份失败: " + e.getMessage(), e);
        }
        return backupFile;
    }

    @Override
    public void restoreSchema(String schemaName, Path backupPath) {
        Path resolved = resolveBackupSource(schemaName, backupPath);
        if (resolved == null || !Files.exists(resolved)) {
            throw new IllegalArgumentException("备份不存在: " + backupPath);
        }

        Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory("dbms_restore_");
        } catch (IOException e) {
            throw new RuntimeException("创建临时目录失败: " + e.getMessage(), e);
        }

        try {
            unzip(resolved, tmpDir);

            // zip 内可能带 schemaName 根目录，也可能直接是文件；两种都处理
            Path extractedRoot = tmpDir.resolve(schemaName);
            Path sourceRoot = Files.exists(extractedRoot) ? extractedRoot : tmpDir;

            Path dataDir = Path.of(StorageEngineConfig.getDATA_DIR());
            Path targetSchemaDir = dataDir.resolve(schemaName);

            if (Files.exists(targetSchemaDir)) {
                deleteRecursively(targetSchemaDir);
            }
            Files.createDirectories(targetSchemaDir);
            copyRecursively(sourceRoot, targetSchemaDir);

            ensureSchemaRegistered(schemaName, targetSchemaDir);
        } catch (IOException e) {
            throw new RuntimeException("还原失败: " + e.getMessage(), e);
        } finally {
            try {
                deleteRecursively(tmpDir);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void deleteBackup(String schemaName, String backupName) {
        if (backupName == null || backupName.isBlank()) {
            return;
        }
        Path schemaBackupDir = backupDir(schemaName);
        Path target = schemaBackupDir.resolve(backupName);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new RuntimeException("删除备份失败: " + e.getMessage(), e);
        }
    }

    private Path backupDir(String schemaName) {
        return Path.of(StorageEngineConfig.getDATA_DIR(), "_backups", schemaName);
    }

    private Path resolveBackupTarget(String schemaName, Path targetDir) {
        String raw = targetDir == null ? "" : targetDir.toString().trim();
        boolean auto = raw.isBlank() || raw.equalsIgnoreCase("auto");
        String fileName = schemaName + "_" + TS.format(Instant.now()) + ".bak";

        if (auto) {
            return backupDir(schemaName).resolve(fileName);
        }

        Path base = targetDir;
        if (!base.isAbsolute()) {
            base = backupDir(schemaName).resolve(base);
        }

        String baseName = base.getFileName() == null ? "" : base.getFileName().toString();
        if (baseName.toLowerCase().endsWith(".bak")) {
            return base;
        }
        return base.resolve(fileName);
    }

    private Path resolveBackupSource(String schemaName, Path backupPath) {
        if (backupPath == null) {
            return null;
        }
        // 'auto' 关键字：自动选择最新的 .bak 文件
        String raw = backupPath.toString().trim();
        if (raw.isBlank() || raw.equalsIgnoreCase("auto")) {
            Path schemaBackupDir = backupDir(schemaName);
            if (!Files.exists(schemaBackupDir)) {
                return null;
            }
            try (var stream = Files.list(schemaBackupDir)) {
                var latest = stream
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".bak"))
                        .max((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                            } catch (IOException e) {
                                return 0;
                            }
                        });
                if (latest.isPresent()) {
                    return latest.get();
                }
            } catch (IOException e) {
                throw new RuntimeException("读取备份目录失败: " + e.getMessage(), e);
            }
            return null;
        }

        if (backupPath.isAbsolute() && Files.exists(backupPath)) {
            return backupPath;
        }
        Path candidate = backupDir(schemaName).resolve(backupPath.toString());
        if (Files.exists(candidate)) {
            return candidate;
        }
        return backupPath;
    }

    private void writeZip(Path sourceDir, Path zipFile, String schemaName) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            Files.walk(sourceDir)
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(p -> {
                        String relative = sourceDir.relativize(p).toString().replace('\\', '/');
                        String entryName = schemaName + "/" + relative;
                        try {
                            zos.putNextEntry(new ZipEntry(entryName));
                            try (FileInputStream fis = new FileInputStream(p.toFile())) {
                                fis.transferTo(zos);
                            }
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw ex;
        }
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        zis.transferTo(fos);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void copyRecursively(Path sourceDir, Path targetDir) throws IOException {
        Files.walk(sourceDir).forEach(p -> {
            try {
                Path rel = sourceDir.relativize(p);
                if (rel.toString().isBlank()) {
                    return;
                }
                Path dest = targetDir.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void ensureSchemaRegistered(String schemaName, Path schemaDir) {
        String globalDbFile = StorageEngineConfig.getGLOBAL_DB_FILE();
        File dbFile = new File(globalDbFile);
        try {
            if (!dbFile.exists()) {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            }
            if (schemaExistsInGlobalDb(dbFile, schemaName)) {
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(dbFile, "rw")) {
                raf.seek(raf.length());
                BinaryIoUtils.writeFixedString(raf, schemaName, 128);
                BinaryIoUtils.writeBool(raf, true);
                BinaryIoUtils.writeFixedString(raf, schemaDir.toFile().getAbsolutePath(), 256);
                BinaryIoUtils.writeDateTime(raf, System.currentTimeMillis());
            }
        } catch (IOException e) {
            // 注册失败不阻断还原结果
        }
    }

    private boolean schemaExistsInGlobalDb(File dbFile, String schemaName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(dbFile, "r")) {
            long length = raf.length();
            long pos = 0;
            while (pos + DB_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(schemaName)) {
                    return true;
                }
                pos += DB_BLOCK_SIZE;
            }
        }
        return false;
    }
}
