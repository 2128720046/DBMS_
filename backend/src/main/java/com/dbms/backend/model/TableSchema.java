package com.dbms.backend.model;

import java.util.List;

/**
 * 表结构聚合对象?
 * <p>
 * 该对象用于在一次调用中返回完整的表结构信息，包括表元数据、字段定义列表和约束定义列表?
 * 主要用于表结构展示和建表结果返回?
 */
public class TableSchema {

    private TableMeta tableMeta;
    private List<FieldDefinition> fields;
    private List<ConstraintDefinition> constraints;

    public TableMeta getTableMeta() {
        return tableMeta;
    }

    public void setTableMeta(TableMeta tableMeta) {
        this.tableMeta = tableMeta;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }

    public void setFields(List<FieldDefinition> fields) {
        this.fields = fields;
    }

    public List<ConstraintDefinition> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<ConstraintDefinition> constraints) {
        this.constraints = constraints;
    }
}
