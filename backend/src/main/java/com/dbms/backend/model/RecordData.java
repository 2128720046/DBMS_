package com.dbms.backend.model;

import java.util.Map;

/**
 * 记录数据对象?
 * <p>
 * 该对象用于表示一张表中的一条逻辑记录，values 使用字段名到字段值的映射方式存储?
 * 便于在字段顺序变化时仍保持较稳定的调用方式?
 */
public class RecordData {

    private String tableName;
    private Map<String, Object> values;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }
}
