package com.dbms.backend.infrastructure.storage;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.EngineCapabilityPolicy;
import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class JdbcSchemaGateway implements DatabaseSchemaGateway {

    private final DatabaseDomainService domainService;
    private final EngineCapabilityPolicy capabilityPolicy;
    private final JdbcTemplate jdbcTemplate;

    public JdbcSchemaGateway(DatabaseDomainService domainService,
                             EngineCapabilityPolicy capabilityPolicy,
                             JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.capabilityPolicy = capabilityPolicy;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createSchema(String schemaName) {
        capabilityPolicy.assertSchemaEnabled();
        String normalized = domainService.normalizeDatabaseName(schemaName);
        String sql = "CREATE SCHEMA IF NOT EXISTS " + domainService.quoteIdentifier(normalized, "数据库名");        capabilityPolicy.assertSqlAllowed(sql, java.util.Set.of("CREATE SCHEMA"));
        jdbcTemplate.execute(sql);
    }

    @Override
    public void dropSchema(String schemaName) {
        capabilityPolicy.assertSchemaEnabled();
        String normalized = domainService.normalizeDatabaseName(schemaName);
        String sql = "DROP SCHEMA IF EXISTS " + domainService.quoteIdentifier(normalized, "数据库名") + " CASCADE";
        capabilityPolicy.assertSqlAllowed(sql, java.util.Set.of("DROP SCHEMA"));
        jdbcTemplate.execute(sql);
    }

    @Override
    public List<String> listSchemas() {
    capabilityPolicy.assertSchemaEnabled();
    return jdbcTemplate.queryForList(
        "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA " +
        "WHERE SCHEMA_NAME NOT IN ('INFORMATION_SCHEMA', 'PUBLIC') " +
        "ORDER BY SCHEMA_NAME",
        String.class
    );
}

    private String quoteSchema(String schemaName) {
        return domainService.quoteIdentifier(schemaName, "数据库名");
    }
}