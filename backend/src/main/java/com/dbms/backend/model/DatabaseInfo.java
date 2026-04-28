package com.dbms.backend.model;

public class DatabaseInfo {

    private String name;

    public DatabaseInfo() {
    }

    public DatabaseInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}