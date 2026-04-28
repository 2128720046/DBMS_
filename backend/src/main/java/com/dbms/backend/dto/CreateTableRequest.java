package com.dbms.backend.dto;

import java.util.List;

public class CreateTableRequest {

    /**
     * 兼容旧字段。
     */
    private String tableName;
    /**
     * 对齐 REST 协议字段：name。
     */
    private String name;
    /**
     * 表注释，当前版本用于展示，不参与 SQL 生成。
     */
    private String comment;
    private List<ColumnDefinition> columns;

    /**
     * @return 表名，优先协议字段 name，其次兼容 tableName。
     */
    public String getTableName() {
        return name != null && !name.isBlank() ? name : tableName;
    }

    /**
     * @param tableName 旧版本请求中的表名字段。
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * @return 协议字段表名。
     */
    public String getName() {
        return name;
    }

    /**
     * @param name REST 协议中的表名字段。
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 表注释。
     */
    public String getComment() {
        return comment;
    }

    /**
     * @param comment 表注释，由前端表单传入。
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * @return 列定义集合，来源于前端建表表单。
     */
    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    /**
     * @param columns 列定义集合。
     */
    public void setColumns(List<ColumnDefinition> columns) {
        this.columns = columns;
    }
}
