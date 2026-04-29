package com.dbms.backend.infrastructure.storage.gateway;

import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import com.dbms.backend.infrastructure.storage.config.StorageEngineConfig;
import com.dbms.backend.infrastructure.storage.io.BinaryIoUtils;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 中期方案：完全脱离 H2 JDBC 驱动的手写 Java 原生二进制 I/O 实现。
 * 满足文档 3.2.1, 3.2.2 与 3.12.4 要求 (ruanko.db 和 data/ 文件夹管理)
 * 
 * 系统库名称可通过 application.yml 配置：dbms.engine.storage.system-schema-name=errDB
 */
@Primary
@Repository
public class NativeDatabaseSchemaGatewayImpl implements DatabaseSchemaGateway {

    public NativeDatabaseSchemaGatewayImpl() {
        StorageEngineConfig.initializeRoot();
        // 注意：不再自动创建系统库，避免每次启动都写入 ruanko.db
        // 系统库由用户通过 createSchema 接口显式创建
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
            throw new RuntimeException("创建数据库文件夹失败: " + dbFolder.getAbsolutePath());
        }

        // 3.12.3(2)：建库时必须创建 [库名].tb 与 [库名].log
        File tbFile = new File(dbFolder, schemaName + ".tb");
        File logFile = new File(dbFolder, schemaName + ".log");
        try {
            if (!tbFile.exists()) tbFile.createNewFile();
            if (!logFile.exists()) logFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException("创建 .tb/.log 失败: " + e.getMessage(), e);
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
            throw new RuntimeException("删除数据库信息失败: " + e.getMessage(), e);
        }
        
        // 物理删除文件夹
        File dbFolder = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
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