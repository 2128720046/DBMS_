package com.dbms.backend.modules.database.infrastructure;

import com.dbms.backend.modules.database.domain.DatabaseSchemaGateway;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.config.StorageEngineProperties;
import com.dbms.backend.core.storage.io.BinaryIoUtils;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary storage implementation of database schema operations.
 */
@Primary
@Repository
public class NativeDatabaseSchemaGatewayImpl implements DatabaseSchemaGateway {

    public NativeDatabaseSchemaGatewayImpl(StorageEngineProperties properties) {
        StorageEngineConfig.initializeRoot();
        if (Boolean.TRUE.equals(properties.getAutoCreateSystemSchema())) {
            try {
                ensureSchemaExists(StorageEngineConfig.getSystemSchemaName());
            } catch (Exception e) {
                // 初始化失败不中断启动
            }
        }
    }

    /**
     * 获取系统库名称，优先使用配置值，否则使用默认值 errDB
     */
    private static String getSystemSchemaName() {
        return StorageEngineConfig.getSystemSchemaName();
    }

    /**
     * DatabaseBlock 定义 (Total 128 + 1 + 256 + 16 = 401 字节)
     * name        CHAR[128]
     * type        BOOL (1 byte)
     * filename    CHAR[256]
     * crtime      DATETIME (16 bytes)
     */
    private static final int DB_BLOCK_SIZE = 401;

    private boolean isSystemSchema(String schemaName) {
        return schemaName != null && getSystemSchemaName().equalsIgnoreCase(schemaName);
    }

    private void ensureSchemaFiles(String schemaName) {
        File dbFolder = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        if (!dbFolder.exists() && !dbFolder.mkdirs()) {
            throw new RuntimeException("创建数据库文件夹失败：" + dbFolder.getAbsolutePath());
        }

        // 3.12.3(2)：建库时必须创建 [库名].tb 与 [库名].log
        File tbFile = new File(dbFolder, schemaName + ".tb");
        File logFile = new File(dbFolder, schemaName + ".log");
        try {
            if (!tbFile.exists()) tbFile.createNewFile();
            if (!logFile.exists()) logFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException("创建 .tb/.log 失败：" + e.getMessage(), e);
        }
        
        // 写入建库日志
        writeLog(logFile, "CREATE_DATABASE", schemaName, "数据库创建成功");
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

    private boolean schemaExistsInGlobalDb(String schemaName) {
        File dbFile = new File(StorageEngineConfig.getGLOBAL_DB_FILE());
        if (!dbFile.exists()) return false;
        try (RandomAccessFile raf = new RandomAccessFile(dbFile, "r")) {
            long length = raf.length();
            long pos = 0;
            while (pos + DB_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (!name.isEmpty() && schemaName.equals(name)) {
                    return true;
                }
                pos += DB_BLOCK_SIZE;
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("读取 ruanko.db 失败: " + e.getMessage(), e);
        }
    }

    private void ensureSchemaExists(String schemaName) {
        if (schemaName == null || schemaName.isEmpty() || schemaName.length() > 128) {
            throw new IllegalArgumentException("数据库名称不能为空或长度大于128限制");
        }

        // 无论是否已登记，都先确保文件体系存在
        ensureSchemaFiles(schemaName);

        // 已登记则不重复追加 DatabaseBlock
        if (schemaExistsInGlobalDb(schemaName)) {
            return;
        }

        File dbFolder = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        boolean isUserDb = !isSystemSchema(schemaName);
        File dbFile = new File(StorageEngineConfig.getGLOBAL_DB_FILE());
        try (RandomAccessFile raf = new RandomAccessFile(dbFile, "rw")) {
            raf.seek(raf.length());
            BinaryIoUtils.writeFixedString(raf, schemaName, 128);                 // name
            BinaryIoUtils.writeBool(raf, isUserDb);                               // type: true=user, false=sys
            BinaryIoUtils.writeFixedString(raf, dbFolder.getAbsolutePath(), 256); // filename
            BinaryIoUtils.writeDateTime(raf, System.currentTimeMillis());         // crtime
        } catch (IOException e) {
            throw new RuntimeException("创建数据库二进制文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void createSchema(String schemaName) {
        ensureSchemaExists(schemaName);
    }

    @Override
    public void dropSchema(String schemaName) {
        if (getSystemSchemaName().equalsIgnoreCase(schemaName)) {
            throw new IllegalArgumentException("系统数据库不允许删除");
        }
        
        File dbFolder = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File logFile = new File(dbFolder, schemaName + ".log");
        
        // 先写日志，再执行删除
        writeLog(logFile, "DROP_DATABASE", schemaName, "数据库删除成功");
        
        File dbFile = new File(StorageEngineConfig.getGLOBAL_DB_FILE());
        try (RandomAccessFile raf = new RandomAccessFile(dbFile, "rw")) {
            long length = raf.length();
            long pos = 0;
            while (pos + DB_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (schemaName.equals(name)) {
                    raf.seek(pos);
                    BinaryIoUtils.writeFixedString(raf, "", 128);
                    break;
                }
                pos += DB_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("删除数据库信息失败：" + e.getMessage(), e);
        }
        
        // 物理删除文件夹
        if (dbFolder.exists()) {
            deleteFolder(dbFolder);
        }
    }

    @Override
    public List<String> listSchemas() {
        List<String> schemas = new ArrayList<>();
        File dbFile = new File(StorageEngineConfig.getGLOBAL_DB_FILE());
        if (!dbFile.exists()) return schemas;

        try (RandomAccessFile raf = new RandomAccessFile(dbFile, "r")) {
            long length = raf.length();
            long pos = 0;
            while (pos + DB_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                boolean type = BinaryIoUtils.readBool(raf);
                String filename = BinaryIoUtils.readFixedString(raf, 256);
                long crtime = BinaryIoUtils.readDateTime(raf);

                if (!name.isEmpty()) {
                    schemas.add(name);
                }
                pos += DB_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("读取数据库列表失败: " + e.getMessage(), e);
        }
        return schemas;
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File fn : files) {
                if (fn.isDirectory()) {
                    deleteFolder(fn);
                } else {
                    fn.delete();
                }
            }
        }
        folder.delete();
    }
}


