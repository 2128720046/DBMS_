package com.dbms.backend.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Capability gates and SQL allowlist checks.
 */
/**
 * 引擎能力策略，控制数据库引擎按模块是否可用。
 * <p>
 * 通过配置项动态管理 schema（数据库）、表、记录等能力是否启用，
 * 适用于环境限制或功能开关场景。同时提供 SQL 语句白名单校验功能。
 * </p>
 *
 * @author DBMS Team
 */
@Component
public class EngineCapabilityPolicy {

    /** 数据库（Schema）能力是否启用，默认启用 */
    @Value("${dbms.engine.capabilities.schema:true}")
    private boolean schemaEnabled;

    /** 数据表能力是否启用，默认启用 */
    @Value("${dbms.engine.capabilities.table:true}")
    private boolean tableEnabled;

    /** 记录（数据行）能力是否启用，默认启用 */
    @Value("${dbms.engine.capabilities.record:true}")
    private boolean recordEnabled;

    /**
     * 断言数据库 Schema 能力可用。
     *
     * @throws IllegalStateException 如果 Schema 能力未启用
     */
    public void assertSchemaEnabled() {
        if (!schemaEnabled) {
            throw new IllegalStateException("当前环境未开启数据库(schema)能力");
        }
    }

    /**
     * 断言数据表能力可用。
     *
     * @throws IllegalStateException 如果表能力未启用
     */
    public void assertTableEnabled() {
        if (!tableEnabled) {
            throw new IllegalStateException("当前环境未开启数据表能力");
        }
    }

    /**
     * 断言记录（数据行）能力可用。
     *
     * @throws IllegalStateException 如果记录能力未启用
     */
    public void assertRecordEnabled() {
        if (!recordEnabled) {
            throw new IllegalStateException("当前环境未开启记录能力");
        }
    }

    /**
     * 校验 SQL 语句是否在允许执行的白名单前缀范围内。
     * <p>
     * 对 SQL 语句去除首尾空白并转换为大写后，逐前缀匹配。
     * </p>
     *
     * @param sql             SQL 语句
     * @param allowedPrefixes 允许的 SQL 前缀集合
     * @throws IllegalArgumentException 如果 SQL 为空或不在允许范围内
     */
    public void assertSqlAllowed(String sql, Set<String> allowedPrefixes) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (allowedPrefixes == null || allowedPrefixes.isEmpty()) {
            return;
        }

        String normalized = sql.trim().toUpperCase(Locale.ROOT);
        for (String prefix : allowedPrefixes) {
            if (normalized.startsWith(prefix.toUpperCase(Locale.ROOT))) {
                return;
            }
        }
        throw new IllegalArgumentException("SQL 不在允许范围内");
    }
}