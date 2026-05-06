package com.dbms.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "创建或更新数据表请求")
public class CreateTableRequest {

    /**
     * 兼容旧字段。
     */
    @Schema(description = "旧协议表名", example = "users")
    private String tableName;
    /**
     * 对齐 REST 协议字段：name。
     */
    @Schema(description = "表名", example = "users")
    private String name;
    /**
     * 表注释，当前版本用于展示，不参与 SQL 生成。
     */
    @Schema(description = "表注释", example = "用户表")
    private String comment;
    @Schema(description = "列定义列表")
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
