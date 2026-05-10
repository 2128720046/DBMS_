package com.dbms.backend.modules.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Column definition used by SQL DDL parsing and storage.
 */
@Schema(description = "表字段定义")
public class ColumnDefinition {

    /**
     * 字段名称。
     */
    @Schema(description = "字段名称", example = "id")
    private String name;
    /**
     * 字段类型（如 INT、VARCHAR）。
     */
    @Schema(description = "字段类型", example = "VARCHAR")
    private String type;
    /**
     * 是否允许空值（true 允许，false 不允许）。
     */
    @Schema(description = "是否允许空值", example = "true")
    private Boolean nullable = true;
    /**
     * 协议扩展字段：是否主键。
     */
    @Schema(description = "是否主键", example = "false")
    private Boolean pk = false;
    /**
     * 协议扩展字段：是否唯一。
     */
    @Schema(description = "是否唯一", example = "false")
    private Boolean uq = false;
    /**
     * 协议扩展字段：长度。
     */
    @Schema(description = "字段长度", example = "32")
    private Integer length;

    /**
     * 用户定义 CHECK 约束表达式（列级）。
     */
    @Schema(description = "CHECK 约束表达式", example = "CHECK (age >= 0)")
    private String checkExpression;

    /**
     * 外键引用表名（列级）。
     */
    @Schema(description = "外键引用表", example = "dept")
    private String foreignKeyTable;

    /**
     * 外键引用列名（列级）。
     */
    @Schema(description = "外键引用列", example = "id")
    private String foreignKeyColumn;

    /**
     * @return 字段名称。
     */
    public String getName() {
        return name;
    }

    /**
     * @param name 字段名称，来源前端建表字段定义。
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 字段类型。
     */
    public String getType() {
        if (type != null && length != null && length > 0 && !type.contains("(")) {
            return type + "(" + length + ")";
        }
        return type;
    }

    /**
     * @param type 字段类型。
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return 是否允许空。
     */
    public Boolean getNullable() {
        if (pk != null && pk) {
            return false;
        }
        return nullable;
    }

    /**
     * @param nullable 是否允许空值。
     */
    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    /**
     * @return 是否主键。
     */
    public Boolean getPk() {
        return pk;
    }

    /**
     * @param pk 协议字段 pk。
     */
    public void setPk(Boolean pk) {
        this.pk = pk;
    }

    /**
     * @return 是否唯一。
     */
    public Boolean getUq() {
        return uq;
    }

    /**
     * @param uq 协议字段 uq。
     */
    public void setUq(Boolean uq) {
        this.uq = uq;
    }

    /**
     * @return 字段长度。
     */
    public Integer getLength() {
        return length;
    }

    /**
     * @param length 字段长度。
     */
    public void setLength(Integer length) {
        this.length = length;
    }

    /**
     * @return CHECK 约束表达式。
     */
    public String getCheckExpression() {
        return checkExpression;
    }

    /**
     * @param checkExpression CHECK 表达式。
     */
    public void setCheckExpression(String checkExpression) {
        this.checkExpression = checkExpression;
    }

    /**
     * @return 外键引用表名。
     */
    public String getForeignKeyTable() {
        return foreignKeyTable;
    }

    /**
     * @param foreignKeyTable 外键引用表名。
     */
    public void setForeignKeyTable(String foreignKeyTable) {
        this.foreignKeyTable = foreignKeyTable;
    }

    /**
     * @return 外键引用列名。
     */
    public String getForeignKeyColumn() {
        return foreignKeyColumn;
    }

    /**
     * @param foreignKeyColumn 外键引用列名。
     */
    public void setForeignKeyColumn(String foreignKeyColumn) {
        this.foreignKeyColumn = foreignKeyColumn;
    }
}



