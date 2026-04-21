package com.dbms.backend.model;

import java.util.List;

/**
 * 约束定义对象?
 * <p>
 * 该对象描述一条完整性约束规则，可用于主键、外键、唯一、非空、检查表达式等约束定义，
 * 主要由表结构管理和完整性校验模块共同使用?
 */
public class ConstraintDefinition {

    private String constraintName;
    private String constraintType;
    private List<String> fieldNames;
    private String referenceTable;
    private List<String> referenceFields;
    private String expression;

    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public String getConstraintType() {
        return constraintType;
    }

    public void setConstraintType(String constraintType) {
        this.constraintType = constraintType;
    }

    public List<String> getFieldNames() {
        return fieldNames;
    }

    public void setFieldNames(List<String> fieldNames) {
        this.fieldNames = fieldNames;
    }

    public String getReferenceTable() {
        return referenceTable;
    }

    public void setReferenceTable(String referenceTable) {
        this.referenceTable = referenceTable;
    }

    public List<String> getReferenceFields() {
        return referenceFields;
    }

    public void setReferenceFields(List<String> referenceFields) {
        this.referenceFields = referenceFields;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
