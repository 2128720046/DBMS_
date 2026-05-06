package com.dbms.backend.domain;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Domain rules for database and identifier validation.
 */
/**
 * 数据库领域服务，负责数据库名和标识符的校验、规范化与引用。
 * <p>
 * 提供数据库名和标识符（表名、列名等）的合法性校验，
 * 并统一转换为大写形式，以保持底层存储层的命名规范。
 * </p>
 *
 * @author DBMS Team
 */
@Service
public class DatabaseDomainService {

    /** 数据库名称正则表达式：字母开头，字母数字下划线，最长 32 位 */
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");

    /** 标识符正则表达式：字母开头，字母数字下划线，最长 64 位 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    /**
     * 校验数据库名称的合法性。
     *
     * @param dbName 数据库名称
     * @throws IllegalArgumentException 如果名称为空或不符合命名规范
     */
    public void validateDatabaseName(String dbName) {
        validateIdentifierByPattern(dbName, "数据库名", DB_NAME_PATTERN,
                "数据库名不合法，仅支持字母开头，字母数字下划线，最长 32 位");
    }

    /**
     * 规范化数据库名称（统一转换为大写）。
     * <p>
     * 存储层以规范化的大写形式处理标识符。
     * </p>
     *
     * @param dbName 数据库名称
     * @return 规范化后的数据库名称（大写）
     * @throws IllegalArgumentException 如果名称为空或不符合命名规范
     */
    public String normalizeDatabaseName(String dbName) {
        validateDatabaseName(dbName);
        return dbName.toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化标识符（表名、列名等，统一转换为大写）。
     * <p>
     * 表名和列名使用与数据库名相同的大写约定。
     * </p>
     *
     * @param identifier 标识符
     * @return 规范化后的标识符（大写）
     * @throws IllegalArgumentException 如果标识符为空或不符合命名规范
     */
    public String normalizeIdentifier(String identifier) {
        validateIdentifier(identifier, "标识符");
        return identifier.toUpperCase(Locale.ROOT);
    }

    /**
     * 校验标识符的合法性。
     *
     * @param identifier 标识符
     * @param fieldName  字段名称（用于错误提示）
     * @throws IllegalArgumentException 如果标识符为空或不符合命名规范
     */
    public void validateIdentifier(String identifier, String fieldName) {
        validateIdentifierByPattern(identifier, fieldName, IDENTIFIER_PATTERN,
                fieldName + "不合法，仅支持字母开头，字母数字下划线，最长 64 位");
    }

    /**
     * 引用标识符（添加双引号并转换为大写），用于 SQL 拼接。
     *
     * @param identifier 标识符
     * @param fieldName  字段名称（用于错误提示）
     * @return 带双引号的引用标识符
     * @throws IllegalArgumentException 如果标识符为空或不符合命名规范
     */
    public String quoteIdentifier(String identifier, String fieldName) {
        validateIdentifier(identifier, fieldName);
        return '"' + identifier.toUpperCase(Locale.ROOT) + '"';
    }

    /**
     * 根据正则表达式校验标识符名称。
     *
     * @param name           待校验的名称
     * @param fieldName      字段名称（用于错误提示）
     * @param pattern        正则表达式
     * @param invalidMessage 校验失败时的错误信息
     * @throws IllegalArgumentException 如果名称为空或不匹配正则表达式
     */
    private void validateIdentifierByPattern(String name, String fieldName, Pattern pattern, String invalidMessage) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (!pattern.matcher(name).matches()) {
            throw new IllegalArgumentException(invalidMessage);
        }
    }
}