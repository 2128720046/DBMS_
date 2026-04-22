package com.dbms.backend.domain;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DatabaseDomainService {

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");

    public void validateDatabaseName(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalArgumentException("数据库名不能为空");
        }
        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            throw new IllegalArgumentException("数据库名不合法，仅支持字母开头，字母数字下划线，最长 32 位");
        }
    }
}