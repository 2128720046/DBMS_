package com.dbms.backend.infrastructure.storage.gateway;

import com.dbms.backend.domain.spi.TableGateway;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.infrastructure.storage.config.StorageEngineConfig;
import com.dbms.backend.infrastructure.storage.io.BinaryIoUtils;

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

    @Override
    public void createTable(String schemaName, String tableName, List<ColumnDefinition> columns) {
        // 第一步：构建文件夹和文件物理路径的检查，没有库就不让建表
        String dbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName;
        File dbDir = new File(dbPath);
        if (!dbDir.exists()) throw new IllegalArgumentException("当前数据库不存在：" + schemaName);

        String tbFilePath = dbPath + File.separator + schemaName + ".tb";
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
                
                // 完整性暂时写入 1 (例如为主键) 或 0
                int integrity = (col.getPk() != null && col.getPk()) ? 1 : 0;
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
        // 重写 .tdf 文件实现表结构变更
        String dbPath = StorageEngineConfig.getDATA_DIR() + File.separator + schemaName;
        String tdfPath = dbPath + File.separator + tableName + ".tdf";
        File tdfFile = new File(tdfPath);
        
        if (!tdfFile.exists()) {
            throw new IllegalArgumentException("表定义文件不存在：" + tdfPath);
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
                if (col.getPk() != null && col.getPk()) integrity |= 2;   // bit1 = PK
                if (col.getNullable() != null && !col.getNullable()) integrity |= 1; // bit0 = NOT NULL
                tdfRaf.writeInt(integrity);                               // integrities
            }
        } catch (IOException e) {
            throw new RuntimeException("更新表定义文件(.tdf)失败：" + e.getMessage(), e);
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
