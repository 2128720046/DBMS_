package com.dbms.backend.model;

import java.util.List;

/**
 * 查询条件对象?
 * <p>
 * 该对象用于描述查询、更新、删除等操作中的条件表达式，支持单条件与多条件组合，
 * 后续可扩展为树形结构以表?AND、OR 等复杂逻辑关系?
 */
public class QueryCondition {

    private String fieldName;
    private String operator;
    private Object value;
    private String logicalOperator;
    private List<QueryCondition> childrenConditions;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getLogicalOperator() {
        return logicalOperator;
    }

    public void setLogicalOperator(String logicalOperator) {
        this.logicalOperator = logicalOperator;
    }

    public List<QueryCondition> getChildrenConditions() {
        return childrenConditions;
    }

    public void setChildrenConditions(List<QueryCondition> childrenConditions) {
        this.childrenConditions = childrenConditions;
    }
}
