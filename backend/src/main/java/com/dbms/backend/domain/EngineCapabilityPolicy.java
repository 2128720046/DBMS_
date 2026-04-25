package com.dbms.backend.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class EngineCapabilityPolicy {

    @Value("${dbms.engine.capabilities.schema:true}")
    private boolean schemaEnabled;

    @Value("${dbms.engine.capabilities.table:true}")
    private boolean tableEnabled;

    @Value("${dbms.engine.capabilities.record:true}")
    private boolean recordEnabled;

    public void assertSchemaEnabled() {
        if (!schemaEnabled) {
            throw new IllegalStateException("当前环境未开启数据库(schema)能力");
        }
    }

    public void assertTableEnabled() {
        if (!tableEnabled) {
            throw new IllegalStateException("当前环境未开启数据表能力");
        }
    }

    public void assertRecordEnabled() {
        if (!recordEnabled) {
            throw new IllegalStateException("当前环境未开启记录能力");
        }
    }

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
