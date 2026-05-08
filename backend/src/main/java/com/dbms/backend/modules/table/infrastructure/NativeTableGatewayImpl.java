package com.dbms.backend.modules.table.infrastructure;

import com.dbms.backend.modules.table.domain.TableGateway;
import com.dbms.backend.modules.table.dto.ColumnDefinition;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.io.BinaryIoUtils;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary storage implementation of table operations.
 */
/**
 * 中期方案：表与字段的原生二进制管理器。
 * 严格对应文档 3.3，3.4，3.12.5 (表名.tb) 和 3.12.6 (表名.tdf) 要求。
 */
@Primary
@Repository
public class NativeTableGatewayImpl implements TableGateway {

    // TableBlock: 128(name)+4(record_num)+4(field_num)+256*4(4个路径)+16(crtime)+4(mtime) = 1180 bytes
    private static final int TABLE_BLOCK_SIZE = 1180;
    // FieldBlock: 4(order)+128(name)+4(type)+4(param)+16(mtime)+4(integrities) = 160 bytes
    private static final int FIELD_BLOCK_SIZE = 160;

    // 3.12.8 约束块: 128(name)+128(field)+4(type)+256(param) = 516 bytes (刚好是 4 的倍数)
    private static final int TIC_BLOCK_SIZE = 516;

    // 3.12.9 索引块: 128(name)+1(unique)+1(asc)+4(field_num)+256(fields)+256(record_file)+256(index_file)=902
    // 按 4 字节对齐补齐到 904 bytes
    private static final int TID_BLOCK_SIZE = 904;

    // trd 每行记录头部：4 bytes status(int)
    private static final int TRD_ROW_HEADER_SIZE = 4;

    @Override
    public void createTable(String schemaName, String tableName, List<ColumnDefinition> columns) {
        // 第一步：构建文件夹和文件物理路径的检查，没有库就不让建表
        String dbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName;
        File dbDir = new File(dbPath);
        if (!dbDir.exists()) throw new IllegalArgumentException("当前数据库不存在：" + schemaName);

        String tbFilePath = dbPath + File.separator + schemaName + ".tb";
        assertTableNotExists(tbFilePath, tableName);
        File logFile = new File(dbPath, schemaName + ".log");
        
        // 约定俗成的物理文件体系（系统验收要求 3.12.3 强制要求存在）
        String tdfPath = dbPath + File.separator + tableName + ".tdf"; // 表定长规则定义
        String trdPath = dbPath + File.separator + tableName + ".trd"; // 行实体数据文件
        String ticPath = dbPath + File.separator + tableName + ".tic"; // 完整性约束描述
        String tidPath = dbPath + File.separator + tableName + ".tid"; // 物理索引数据

        // 第二步：将表名、四个文件的完整绝对路径、和记录数，全部算好字节位置（1180 字节）然后填入 `dbname.tb` 文件
        try (RandomAccessFile tbRaf = new RandomAccessFile(new File(tbFilePath), "rw")) {
            tbRaf.seek(tbRaf.length());
            BinaryIoUtils.writeFixedString(tbRaf, tableName, 128);  // name
            tbRaf.writeInt(0);                                      // record_num 初始 0
            tbRaf.writeInt(columns.size());                         // field_num
            BinaryIoUtils.writeFixedString(tbRaf, tdfPath, 256);    // tdf
            BinaryIoUtils.writeFixedString(tbRaf, ticPath, 256);    // tic
            BinaryIoUtils.writeFixedString(tbRaf, trdPath, 256);    // trd
            BinaryIoUtils.writeFixedString(tbRaf, tidPath, 256);    // tid
            BinaryIoUtils.writeDateTime(tbRaf, System.currentTimeMillis()); // crtime
            tbRaf.writeInt((int) (System.currentTimeMillis() / 1000));      // mtime (INTEGER)
        } catch (IOException e) {
            throw new RuntimeException("写入表描述文件 (.tb) 失败：" + e.getMessage(), e);
        }

        // 3. 生成字段定义文件 (.tdf)
        try (RandomAccessFile tdfRaf = new RandomAccessFile(new File(tdfPath), "rw")) {
            int order = 1;
            for (ColumnDefinition col : columns) {
                tdfRaf.writeInt(order++);                                 // order
                BinaryIoUtils.writeFixedString(tdfRaf, col.getName(), 128); // name
                
                // 将数据类型转为整数常量 (简易映射：1=INT, 2=BOOL, 3=DOUBLE, 4=VARCHAR, 5=DATETIME)
                int typeCode = getTypeCode(col.getType());
                tdfRaf.writeInt(typeCode);                                // type
                
                // param 存放 VARCHAR 长度等，如果是固定类型就传 0，这里取用对象中的 length
                int param = (col.getLength() != null) ? col.getLength() : 0;
                tdfRaf.writeInt(param);                                   // param
                
                BinaryIoUtils.writeDateTime(tdfRaf, System.currentTimeMillis()); // mtime
                
                // bit0 = NOT NULL, bit1 = PK, bit2 = UNIQUE
                int integrity = 0;
                if (col.getNullable() != null && !col.getNullable()) integrity |= 1;
                if (col.getPk() != null && col.getPk()) integrity |= 2;
                if (col.getUq() != null && col.getUq()) integrity |= 4;
                tdfRaf.writeInt(integrity);                               // integrities
            }
        } catch (Exception e) {
             throw new RuntimeException("写入表定义文件 (.tdf) 失败：" + e.getMessage(), e);
        }

        // 4. 创建其余空的数据文件和索引文件
        createEmptyFile(trdPath);
        ensureTicFileWithPlaceholder(ticPath);
        ensureTidFileWithPlaceholder(tidPath, trdPath);
        // 3.12.9.4 索引数据文件：[索引名].ix，建表时先创建空的（索引创建时再填充）
        // 默认主键索引文件：[tableName]Index.ix
        String defaultIxPath = dbPath + File.separator + tableName + "Index.ix";
        createEmptyFile(defaultIxPath);
        
        // 写入建表日志
        writeLog(logFile, "CREATE_TABLE", tableName, "表创建成功，字段数：" + columns.size());
    }

    @Override
    public void dropTable(String schemaName, String tableName) {
        String dbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName;
        String tbFilePath = dbPath + File.separator + schemaName + ".tb";
        File tbFile = new File(tbFilePath);
        File logFile = new File(dbPath, schemaName + ".log");
        if (!tbFile.exists()) return;

        // 先写日志
        writeLog(logFile, "DROP_TABLE", tableName, "表删除成功");

        // 1. 逻辑删除表描述文件 .tb 里的记录
        try (RandomAccessFile tbRaf = new RandomAccessFile(tbFile, "rw")) {
            long length = tbRaf.length();
            long pos = 0;
            while (pos < length) {
                tbRaf.seek(pos);
                String name = BinaryIoUtils.readFixedString(tbRaf, 128);
                if (tableName.equals(name)) {
                    tbRaf.seek(pos);
                    BinaryIoUtils.writeFixedString(tbRaf, "", 128); // 名称置空作为逻辑删除
                    break;
                }
                pos += TABLE_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("删除表描述失败：" + e.getMessage(), e);
        }

        // 2. 物理删除关联的所有文件（包括 .ix 索引数据文件）
        new File(dbPath + File.separator + tableName + ".tdf").delete();
        new File(dbPath + File.separator + tableName + ".trd").delete();
        new File(dbPath + File.separator + tableName + ".tic").delete();
        new File(dbPath + File.separator + tableName + ".tid").delete();
        // 删除所有可能的 .ix 索引数据文件（主键索引 [tableName]Index.ix 等）
        File dbDir = new File(dbPath);
        File[] ixFiles = dbDir.listFiles((dir, name) -> name.endsWith(".ix") && name.startsWith(tableName));
        if (ixFiles != null) {
            for (File f : ixFiles) f.delete();
        }
    }

    @Override
    public List<String> listTables(String schemaName) {
        List<String> tables = new ArrayList<>();
        String tbFilePath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + schemaName + ".tb";
        File tbFile = new File(tbFilePath);
        if (!tbFile.exists()) return tables;

        try (RandomAccessFile tbRaf = new RandomAccessFile(tbFile, "r")) {
            long length = tbRaf.length();
            long pos = 0;
            while (pos < length) {
                tbRaf.seek(pos);
                String name = BinaryIoUtils.readFixedString(tbRaf, 128);
                if (!name.isEmpty()) {
                    tables.add(name);
                }
                pos += TABLE_BLOCK_SIZE;
            }
        } catch (IOException e) {
           throw new RuntimeException("读取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public void alterTableStructure(String schemaName, String tableName, List<ColumnDefinition> columns) {
        // 重写 .tdf 文件实现表结构变更，同时尽可能迁移已有 .trd 数据，避免结构变更后读取错位
        String dbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName;
        String tdfPath = dbPath + File.separator + tableName + ".tdf";
        File tdfFile = new File(tdfPath);
        String trdPath = dbPath + File.separator + tableName + ".trd";
        File trdFile = new File(trdPath);
        
        if (!tdfFile.exists()) {
            throw new IllegalArgumentException("表定义文件不存在：" + tdfPath);
        }
        
        List<FieldMeta> oldMetas = null;
        List<Map<String, Object>> existingRows = List.of();
        try {
            if (trdFile.exists() && trdFile.length() > 0) {
                oldMetas = parseTdfMeta(tdfFile);
                existingRows = readAllActiveRows(trdFile, oldMetas);
            }
        } catch (Exception ignored) {
            oldMetas = null;
            existingRows = List.of();
        }

        try (RandomAccessFile tdfRaf = new RandomAccessFile(tdfFile, "rw")) {
            tdfRaf.setLength(0); // 清空文件
            
            int order = 1;
            for (ColumnDefinition col : columns) {
                tdfRaf.writeInt(order++);                                 // order
                BinaryIoUtils.writeFixedString(tdfRaf, col.getName(), 128); // name
                
                int typeCode = getTypeCode(col.getType());
                tdfRaf.writeInt(typeCode);                                // type
                
                int param = (col.getLength() != null) ? col.getLength() : 0;
                tdfRaf.writeInt(param);                                   // param
                
                BinaryIoUtils.writeDateTime(tdfRaf, System.currentTimeMillis()); // mtime
                
                int integrity = 0;
                if (col.getNullable() != null && !col.getNullable()) integrity |= 1; // bit0 = NOT NULL
                if (col.getPk() != null && col.getPk()) integrity |= 2;             // bit1 = PK
                if (col.getUq() != null && col.getUq()) integrity |= 4;             // bit2 = UNIQUE
                tdfRaf.writeInt(integrity);                               // integrities
            }
        } catch (IOException e) {
            throw new RuntimeException("更新表定义文件(.tdf)失败：" + e.getMessage(), e);
        }

        // 迁移已有数据：将旧行映射到新字段集合，写回 .trd
        if (existingRows != null && !existingRows.isEmpty()) {
            try {
                List<FieldMeta> newMetas = parseTdfMeta(tdfFile);
                int migrated = rewriteTrd(trdFile, existingRows, newMetas);
                updateTableMetadata(schemaName, tableName, columns.size(), migrated);
            } catch (Exception e) {
                throw new RuntimeException("表结构更新后数据迁移失败：" + e.getMessage(), e);
            }
        } else {
            updateTableMetadata(schemaName, tableName, columns.size(), null);
        }
    }

    private void assertTableNotExists(String tbFilePath, String tableName) {
        File tbFile = new File(tbFilePath);
        if (!tbFile.exists()) {
            throw new IllegalArgumentException("表描述文件不存在：" + tbFilePath);
        }
        try (RandomAccessFile tbRaf = new RandomAccessFile(tbFile, "r")) {
            long length = tbRaf.length();
            long pos = 0;
            while (pos + TABLE_BLOCK_SIZE <= length) {
                tbRaf.seek(pos);
                String name = BinaryIoUtils.readFixedString(tbRaf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(tableName)) {
                    throw new IllegalArgumentException("表已存在：" + tableName);
                }
                pos += TABLE_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("读取表描述失败：" + e.getMessage(), e);
        }
    }

    private void updateTableMetadata(String schemaName, String tableName, int fieldCount, Integer recordCountOrNull) {
        String tbFilePath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName + File.separator + schemaName + ".tb";
        File tbFile = new File(tbFilePath);
        if (!tbFile.exists()) {
            return;
        }
        try (RandomAccessFile tbRaf = new RandomAccessFile(tbFile, "rw")) {
            long length = tbRaf.length();
            long pos = 0;
            while (pos + TABLE_BLOCK_SIZE <= length) {
                tbRaf.seek(pos);
                String name = BinaryIoUtils.readFixedString(tbRaf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(tableName)) {
                    // record_num 在 pos+128
                    if (recordCountOrNull != null) {
                        tbRaf.seek(pos + 128);
                        tbRaf.writeInt(Math.max(0, recordCountOrNull));
                    }
                    // field_num 在 pos+132
                    tbRaf.seek(pos + 128 + 4);
                    tbRaf.writeInt(fieldCount);

                    // mtime 在最后 4 bytes
                    tbRaf.seek(pos + (TABLE_BLOCK_SIZE - 4));
                    tbRaf.writeInt((int) (System.currentTimeMillis() / 1000));
                    return;
                }
                pos += TABLE_BLOCK_SIZE;
            }
        } catch (IOException ignored) {
            // 元数据更新失败不阻断主流程
        }
    }

    private static class FieldMeta {
        String name;
        int type;   // 1=INT, 2=BOOL, 3=DOUBLE, 4=VARCHAR, 5=DATETIME
        int param;
        int length; // padded to multiple of 4
        int offset; // relative to row payload start (after status int)
    }

    private List<FieldMeta> parseTdfMeta(File tdfFile) throws IOException {
        List<FieldMeta> metas = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            int currentOffset = 0;
            while (pos + FIELD_BLOCK_SIZE <= fileLength) {
                raf.seek(pos);
                raf.readInt();
                String name = BinaryIoUtils.readFixedString(raf, 128);
                int type = raf.readInt();
                int param = raf.readInt();
                // skip mtime 16 + integrities 4

                FieldMeta meta = new FieldMeta();
                meta.name = name;
                meta.type = type;
                meta.param = param;
                meta.offset = currentOffset;

                int length;
                switch (type) {
                    case 1: length = 4; break;
                    case 2: length = 1; break;
                    case 3: length = 8; break;
                    case 5: length = 16; break;
                    case 4: default: length = param + 1; break;
                }
                int padding = length % 4;
                if (padding != 0) {
                    length += (4 - padding);
                }
                meta.length = length;
                currentOffset += length;

                metas.add(meta);
                pos += FIELD_BLOCK_SIZE;
            }
        }
        return metas;
    }

    private List<Map<String, Object>> readAllActiveRows(File trdFile, List<FieldMeta> metas) throws IOException {
        if (metas == null || metas.isEmpty()) {
            return List.of();
        }
        int recordLength = TRD_ROW_HEADER_SIZE;
        for (FieldMeta m : metas) {
            recordLength += m.length;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            while (pos + recordLength <= fileLength) {
                raf.seek(pos);
                int status = raf.readInt();
                if (status == 1) {
                    Map<String, Object> row = new HashMap<>();
                    long payloadStart = pos + TRD_ROW_HEADER_SIZE;
                    for (FieldMeta meta : metas) {
                        raf.seek(payloadStart + meta.offset);
                        row.put(meta.name, readValue(raf, meta));
                    }
                    rows.add(row);
                }
                pos += recordLength;
            }
        }
        return rows;
    }

    private Object readValue(RandomAccessFile raf, FieldMeta meta) throws IOException {
        switch (meta.type) {
            case 1:
                return raf.readInt();
            case 2:
                return raf.readByte() != 0;
            case 3:
                return raf.readDouble();
            case 5:
                return BinaryIoUtils.readDateTime(raf);
            case 4:
            default:
                return BinaryIoUtils.readFixedString(raf, meta.param + 1);
        }
    }

    private int rewriteTrd(File trdFile, List<Map<String, Object>> rows, List<FieldMeta> metas) throws IOException {
        File tmp = new File(trdFile.getAbsolutePath() + ".tmp");
        int written = 0;
        try (RandomAccessFile raf = new RandomAccessFile(tmp, "rw")) {
            raf.setLength(0);
            for (Map<String, Object> row : rows) {
                raf.seek(raf.length());
                raf.writeInt(1);
                for (FieldMeta meta : metas) {
                    Object val = row.get(meta.name);
                    long posBefore = raf.getFilePointer();
                    writeValue(raf, meta, val);
                    raf.seek(posBefore + meta.length);
                }
                written++;
            }
        }

        // 覆盖替换（Windows 下 renameTo 可能失败，使用 NIO move 更稳）
        java.nio.file.Files.move(tmp.toPath(), trdFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return written;
    }

    private void writeValue(RandomAccessFile raf, FieldMeta meta, Object val) throws IOException {
        switch (meta.type) {
            case 1: {
                int v = 0;
                if (val instanceof Number n) v = n.intValue();
                else if (val instanceof String s) {
                    try { v = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
                }
                raf.writeInt(v);
                return;
            }
            case 2: {
                boolean v = false;
                if (val instanceof Boolean b) v = b;
                else if (val instanceof Number n) v = n.intValue() != 0;
                else if (val instanceof String s) v = Boolean.parseBoolean(s.trim());
                raf.writeByte(v ? 1 : 0);
                return;
            }
            case 3: {
                double v = 0.0;
                if (val instanceof Number n) v = n.doubleValue();
                else if (val instanceof String s) {
                    try { v = Double.parseDouble(s.trim()); } catch (Exception ignored) {}
                }
                raf.writeDouble(v);
                return;
            }
            case 5: {
                long v = System.currentTimeMillis();
                if (val instanceof Number n) v = n.longValue();
                else if (val instanceof String s) {
                    try { v = Long.parseLong(s.trim()); } catch (Exception ignored) {}
                }
                BinaryIoUtils.writeDateTime(raf, v);
                return;
            }
            case 4:
            default: {
                String v = val == null ? "" : String.valueOf(val);
                BinaryIoUtils.writeFixedString(raf, v, meta.param + 1);
            }
        }
    }

    @Override
    public Map<String, Object> getTableDetail(String schemaName, String tableName) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("tableName", tableName);
        
        try {
            List<Map<String, Object>> columns = readColumnDefinitions(schemaName, tableName);
            detail.put("columns", columns);
        } catch (Exception e) {
            detail.put("columns", new ArrayList<Map<String, Object>>());
            detail.put("error", "读取表定义失败：" + e.getMessage());
        }
        
        return detail;
    }
    
    /**
     * 从 .tdf 文件读取列定义
     */
    private List<Map<String, Object>> readColumnDefinitions(String schemaName, String tableName) throws IOException {
        String dbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName;
        String tdfPath = dbPath + File.separator + tableName + ".tdf";
        File tdfFile = new File(tdfPath);
        
        if (!tdfFile.exists()) {
            throw new IllegalArgumentException("表定义文件不存在：" + tdfPath);
        }
        
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            
            while (pos < fileLength) {
                raf.seek(pos);
                
                int order = raf.readInt();                                  // order
                String name = BinaryIoUtils.readFixedString(raf, 128);      // name
                int typeCode = raf.readInt();                               // type
                int param = raf.readInt();                                  // param
                long mtime = BinaryIoUtils.readDateTime(raf);               // mtime
                int integrities = raf.readInt();                            // integrities
                
                pos += FIELD_BLOCK_SIZE;
                
                if (!name.isEmpty()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("order", order);
                    column.put("name", name);
                    column.put("type", getTypeName(typeCode));
                    column.put("typeCode", typeCode);
                    column.put("length", param > 0 ? param : null);
                    column.put("nullable", (integrities & 1) == 0);         // bit0=0 表示允许空
                    column.put("primaryKey", (integrities & 2) != 0);       // bit1=1 表示主键
                    
                    columns.add(column);
                }
            }
        }
        
        return columns;
    }
    
    /**
     * 将类型代码转换为类型名称
     */
    private String getTypeName(int typeCode) {
        if (typeCode == 1) {
            return "INT";
        } else if (typeCode == 2) {
            return "BOOL";
        } else if (typeCode == 3) {
            return "DOUBLE";
        } else if (typeCode == 4) {
            return "VARCHAR";
        } else if (typeCode == 5) {
            return "DATETIME";
        } else {
            return "UNKNOWN";
        }
    }

    private void createEmptyFile(String path) {
        try {
            new File(path).createNewFile();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 让 .tic 至少具备 1 个可按块读取的占位结构（name 为空视为逻辑删除）。
     */
    private void ensureTicFileWithPlaceholder(String ticPath) {
        File ticFile = new File(ticPath);
        try (RandomAccessFile raf = new RandomAccessFile(ticFile, "rw")) {
            if (raf.length() > 0) return;
            raf.seek(0);
            BinaryIoUtils.writeFixedString(raf, "", 128); // name
            BinaryIoUtils.writeFixedString(raf, "", 128); // field
            raf.writeInt(0);                               // type
            BinaryIoUtils.writeFixedString(raf, "", 256); // param
        } catch (IOException e) {
            throw new RuntimeException("初始化 .tic 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 让 .tid 至少具备 1 个可按块读取的占位结构（name 为空视为逻辑删除），并按 4 字节补齐。
     */
    private void ensureTidFileWithPlaceholder(String tidPath, String trdPath) {
        File tidFile = new File(tidPath);
        try (RandomAccessFile raf = new RandomAccessFile(tidFile, "rw")) {
            if (raf.length() > 0) return;
            raf.seek(0);

            BinaryIoUtils.writeFixedString(raf, "", 128); // name
            BinaryIoUtils.writeBool(raf, false);           // unique
            BinaryIoUtils.writeBool(raf, true);            // asc
            raf.writeInt(0);                               // field_num
            BinaryIoUtils.writeFixedString(raf, "", 128); // fields[0]
            BinaryIoUtils.writeFixedString(raf, "", 128); // fields[1]
            BinaryIoUtils.writeFixedString(raf, trdPath, 256); // record_file
            BinaryIoUtils.writeFixedString(raf, "", 256);      // index_file

            // 902 bytes -> pad 2 bytes to 904
            BinaryIoUtils.writeZeroPadding(raf, 2);
        } catch (IOException e) {
            throw new RuntimeException("初始化 .tid 失败: " + e.getMessage(), e);
        }
    }

    private int getTypeCode(String typeName) {
        if (typeName == null) return 4;
        String upper = typeName.toUpperCase();
        if (upper.contains("INT")) return 1;
        if (upper.contains("BOOL")) return 2;
        if (upper.contains("DOUBLE") || upper.contains("FLOAT")) return 3;
        if (upper.contains("CHAR")) return 4; // VARCHAR / CHAR
        if (upper.contains("DATE") || upper.contains("TIME")) return 5;
        return 4; // 默认做字符串处理
    }
    
    /**
     * 写入数据库日志文件
     * 日志格式：[时间戳] 操作类型 | 对象名称 | 描述
     */
    private void writeLog(File logFile, String operation, String objectName, String description) {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "rw")) {
            raf.seek(raf.length());
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String logLine = String.format("[%s] %s | %s | %s%n", timestamp, operation, objectName, description);
            raf.write(logLine.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 日志写入失败不阻断主流程
        }
    }
}



