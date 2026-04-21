package com.dbms.backend.model;

import java.time.LocalDateTime;

/**
 * 数据库元信息对象?
 * <p>
 * 该对象用于描述一个数据库的基本属性，主要在数据库创建、删除、列表查询?
 * 元数据持久化等场景中使用，对应全局数据库描述文件中的一条记录?
 */
public class DatabaseMeta {

    private String databaseName;
    private String databaseType;
    private String dataPath;
    private LocalDateTime createTime;

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
