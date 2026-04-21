package com.dbms.backend.model;

/**
 * 字段定义对象?
 * <p>
 * 该对象描述表中一个字段的结构信息，包含字段名、顺序、类型、长度以及主键、唯一?
 * 非空、自增等约束属性，主要用于建表、改表、记录校验和记录序列化?
 */
public class FieldDefinition {

    private String fieldName;
    private int fieldOrder;
    private String fieldType;
    private int fieldLength;
    private boolean nullable;
    private boolean unique;
    private String defaultValue;
    private boolean autoIncrement;
    private boolean primaryKey;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public int getFieldOrder() {
        return fieldOrder;
    }

    public void setFieldOrder(int fieldOrder) {
        this.fieldOrder = fieldOrder;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public int getFieldLength() {
        return fieldLength;
    }

    public void setFieldLength(int fieldLength) {
        this.fieldLength = fieldLength;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public void setAutoIncrement(boolean autoIncrement) {
        this.autoIncrement = autoIncrement;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }
}
