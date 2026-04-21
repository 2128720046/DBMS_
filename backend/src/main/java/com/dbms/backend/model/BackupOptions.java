package com.dbms.backend.model;

import java.time.LocalDateTime;

/**
 * 备份配置对象?
 * <p>
 * 该对象用于描述一次备份操作的配置项，包括备份名称、备份路径、是否包含日志文件等?
 * 供备份恢复模块在创建备份包时使用?
 */
public class BackupOptions {

    private String backupName;
    private String backupPath;
    private boolean includeLogFiles;
    private LocalDateTime createTime;

    public String getBackupName() {
        return backupName;
    }

    public void setBackupName(String backupName) {
        this.backupName = backupName;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }

    public boolean isIncludeLogFiles() {
        return includeLogFiles;
    }

    public void setIncludeLogFiles(boolean includeLogFiles) {
        this.includeLogFiles = includeLogFiles;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
