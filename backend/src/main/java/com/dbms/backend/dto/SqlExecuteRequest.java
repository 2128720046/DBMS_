package com.dbms.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SQL execution request payload.
 */
@Schema(description = "SQL 执行请求")
public class SqlExecuteRequest {

    @Schema(description = "数据库名称", example = "test_db")
    private String databaseName;
    @Schema(description = "SQL 语句", example = "SELECT * FROM users;")
    private String sql;

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
