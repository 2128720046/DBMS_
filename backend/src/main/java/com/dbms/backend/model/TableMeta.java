package com.dbms.backend.model;

import java.time.LocalDateTime;

/**
 * 表元信息对象?
 * <p>
 * 该对象用于描述一张数据表的基本属性，包括字段数量、记录数量以及各种定义文件路径，
 * 主要用于表管理、字段管理和元数据持久化场景?
 */
public class TableMeta {

    private String tableName;
    private int fieldCount;
    private int recordCount;
    private String tdfPath;
    private String ticPath;
    private String trdPath;
    private String tidPath;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public String getTdfPath() {
        return tdfPath;
    }

    public void setTdfPath(String tdfPath) {
        this.tdfPath = tdfPath;
    }

    public String getTicPath() {
        return ticPath;
    }

    public void setTicPath(String ticPath) {
        this.ticPath = ticPath;
    }

    public String getTrdPath() {
        return trdPath;
    }

    public void setTrdPath(String trdPath) {
        this.trdPath = trdPath;
    }

    public String getTidPath() {
        return tidPath;
    }

    public void setTidPath(String tidPath) {
        this.tidPath = tidPath;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getModifyTime() {
        return modifyTime;
    }

    public void setModifyTime(LocalDateTime modifyTime) {
        this.modifyTime = modifyTime;
    }
}
