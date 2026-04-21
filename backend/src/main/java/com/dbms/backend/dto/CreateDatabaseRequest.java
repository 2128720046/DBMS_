package com.dbms.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateDatabaseRequest {

    @NotBlank(message = "数据库名称不能为空")
    private String databaseName;

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }
}

