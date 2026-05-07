package com.dbms.backend.core.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds storage configuration from application.yml.
 */
/**
 * DBMS 存储引擎配置属性绑定类
 * 从 application.yml 读取 dbms.engine.storage.* 配置项
 */
@Component
@ConfigurationProperties(prefix = "dbms.engine.storage")
public class StorageEngineProperties {

    /**
     * 系统数据库名称（默认 errDB）
     */
    private String systemSchemaName = "errDB";

    /**
     * 全局数据库注册表文件名（默认 ruanko.db）
     */
    private String globalDbFileName = "ruanko.db";

    /**
     * 数据目录名（默认 data）
     */
    private String dataDirName = "data";

    /**
     * DBMS 根目录，空表示使用程序运行目录
     */
    private String dbmsRoot = "";

    /**
     * 是否自动创建系统数据库
     */
    private Boolean autoCreateSystemSchema = false;

    // ==================== Setter 方法 ====================

    public void setSystemSchemaName(String systemSchemaName) {
        this.systemSchemaName = systemSchemaName;
        StorageEngineConfig.setSystemSchemaName(this.systemSchemaName);
    }

    public void setGlobalDbFileName(String globalDbFileName) {
        this.globalDbFileName = globalDbFileName;
        StorageEngineConfig.setGlobalDbFileName(this.globalDbFileName);
    }

    public void setDataDirName(String dataDirName) {
        this.dataDirName = dataDirName;
        StorageEngineConfig.setDataDirName(this.dataDirName);
    }

    public void setDbmsRoot(String dbmsRoot) {
        this.dbmsRoot = dbmsRoot;
        if (dbmsRoot != null && !dbmsRoot.isBlank()) {
            StorageEngineConfig.setDbmsRoot(dbmsRoot);
        }
    }

    // ==================== Getter 方法 ====================

    public String getSystemSchemaName() {
        return systemSchemaName;
    }

    public String getGlobalDbFileName() {
        return globalDbFileName;
    }

    public String getDataDirName() {
        return dataDirName;
    }

    public String getDbmsRoot() {
        return dbmsRoot;
    }

    public Boolean getAutoCreateSystemSchema() {
        return autoCreateSystemSchema;
    }

    public void setAutoCreateSystemSchema(Boolean autoCreateSystemSchema) {
        this.autoCreateSystemSchema = autoCreateSystemSchema;
    }
}


