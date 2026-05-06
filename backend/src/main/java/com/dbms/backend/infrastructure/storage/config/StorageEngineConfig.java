package com.dbms.backend.infrastructure.storage.config;

import java.io.File;

/**
 * Static storage engine path configuration.
 */
/**
 * DBMS 底层存储引擎配置常量
 * 
 * 配置说明：
 * - DBMS_ROOT: DBMS 程序安装的根目录，所有二进制文件存放在此目录下
 * - SYSTEM_SCHEMA_NAME: 系统数据库名称，默认 errDB
 * - 这些配置可以在 application.yml 中通过 dbms.engine.storage.* 修改
 */
public class StorageEngineConfig {
    
    // 默认配置值
    private static String dbmsRoot = System.getProperty("user.dir");
    private static String systemSchemaName = "errDB";
    private static String globalDbFileName = "ruanko.db";
    private static String dataDirName = "data";
    
    // 运行时计算的完整路径
    private static String globalDbFile;
    private static String dataDir;
    
    /**
     * 设置 DBMS 根目录（由 Spring 配置注入）
     */
    public static void setDbmsRoot(String root) {
        dbmsRoot = root;
        recomputePaths();
    }
    
    /**
     * 设置系统库名称（由 Spring 配置注入）
     */
    public static void setSystemSchemaName(String name) {
        if (name != null && !name.isBlank()) {
            systemSchemaName = name;
        }
    }
    
    /**
     * 设置全局数据库文件名（由 Spring 配置注入，一般不需修改）
     */
    public static void setGlobalDbFileName(String name) {
        if (name != null && !name.isBlank()) {
            globalDbFileName = name;
        }
        recomputePaths();
    }
    
    /**
     * 设置数据目录名（由 Spring 配置注入，一般不需修改）
     */
    public static void setDataDirName(String name) {
        if (name != null && !name.isBlank()) {
            dataDirName = name;
        }
        recomputePaths();
    }
    
    private static void recomputePaths() {
        dataDir = dbmsRoot + File.separator + dataDirName;
        globalDbFile = dataDir + File.separator + globalDbFileName;
    }
    
    // 静态初始化块，初始化路径
    static {
        recomputePaths();
    }
    
    // ==================== Getter 方法 ====================
    
    public static String getDBMS_ROOT() {
        return dbmsRoot;
    }
    
    public static String getGLOBAL_DB_FILE() {
        return globalDbFile;
    }
    
    public static String getDATA_DIR() {
        return dataDir;
    }
    
    public static String getSystemSchemaName() {
        return systemSchemaName;
    }
    
    /**
     * 初始化数据库根目录，确保必要文件夹存在
     * 注意：不再自动创建 ruanko.db 文件，避免残留逻辑
     */
    public static void initializeRoot() {
        File root = new File(dbmsRoot);
        if (!root.exists()) {
            root.mkdirs();
        }

        File dataDirFile = new File(dataDir);
        if (!dataDirFile.exists()) {
            dataDirFile.mkdirs();
        }
    }
}