package com.dbms.backend.model;

/**
 * 索引元信息对象?
 * <p>
 * 该对象描述一个索引的基础属性，包括索引名称、所属表、目标字段以及索引文件路径，
 * 主要用于索引管理模块和索引文件持久化模块?
 */
public class IndexMeta {

    private String indexName;
    private String tableName;
    private String fieldName;
    private boolean unique;
    private String indexFilePath;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getIndexFilePath() {
        return indexFilePath;
    }

    public void setIndexFilePath(String indexFilePath) {
        this.indexFilePath = indexFilePath;
    }
}
