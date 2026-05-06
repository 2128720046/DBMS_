package com.dbms.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建数据库请求")
public class CreateDatabaseRequest {

    /**
     * 兼容旧字段：前端可能仍传 dbName。
     */
    @Schema(description = "旧协议数据库名称", example = "test_db")
    private String dbName;
    /**
     * 对齐 REST 协议字段：name。
     */
    @Schema(description = "数据库名称", example = "test_db")
    private String name;
    /**
     * 字符集字段，当前版本不参与建库执行，仅用于协议兼容。
     */
    @Schema(description = "字符集", example = "utf8mb4")
    private String charset;

    /**
     * @return 数据库名称，优先使用协议字段 name，其次兼容 dbName。
     */
    public String getDbName() {
        return name != null && !name.isBlank() ? name : dbName;
    }

    /**
     * @param dbName 旧版本传入的数据库名。
     */
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    /**
     * @return 协议字段数据库名。
     */
    public String getName() {
        return name;
    }

    /**
     * @param name REST 协议中的数据库名称字段。
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 请求中的字符集参数。
     */
    public String getCharset() {
        return charset;
    }

    /**
     * @param charset 请求中的字符集参数。
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }
}