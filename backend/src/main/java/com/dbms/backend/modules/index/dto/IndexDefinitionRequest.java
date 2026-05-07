package com.dbms.backend.modules.index.dto;

import java.util.List;

/**
 * 索引定义请求对象。
 * <p>
 * 供 SQL 执行层、未来 REST/桌面协议层或测试代码复用。后续只需要补充字段校验和
 * 与 .tid/.ix 文件格式之间的映射，不需要再创建 DTO 文件。
 * </p>
 */
public class IndexDefinitionRequest {
    private String name;
    private String tableName;
    private List<String> columns;
    private boolean unique;
    private boolean ascending = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }
}
