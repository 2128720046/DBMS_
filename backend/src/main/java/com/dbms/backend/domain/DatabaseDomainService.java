package com.dbms.backend.domain;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DatabaseDomainService {

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public void validateDatabaseName(String dbName) {
        validateIdentifierByPattern(dbName, "数据库名", DB_NAME_PATTERN,
                "数据库名不合法，仅支持字母开头，字母数字下划线，最长 32 位");
    }

    public void validateIdentifier(String identifier, String fieldName) {
        validateIdentifierByPattern(identifier, fieldName, IDENTIFIER_PATTERN,
                fieldName + "不合法，仅支持字母开头，字母数字下划线，最长 64 位");
    }

    public String quoteIdentifier(String identifier, String fieldName) {
        validateIdentifier(identifier, fieldName);
        return '"' + identifier + '"';
    }

    private void validateIdentifierByPattern(String name, String fieldName, Pattern pattern, String invalidMessage) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (!pattern.matcher(name).matches()) {
            throw new IllegalArgumentException(invalidMessage);
        }
    }
    public String normalizeDatabaseName(String dbName) {
    validateDatabaseName(dbName);
    return dbName.toUpperCase();   // 统一转大写
}
}