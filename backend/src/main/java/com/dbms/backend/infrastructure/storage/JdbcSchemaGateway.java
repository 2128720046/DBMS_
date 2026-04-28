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
        String sql = "CREATE SCHEMA IF NOT EXISTS " + quoteSchema(schemaName);
        capabilityPolicy.assertSqlAllowed(sql, java.util.Set.of("CREATE SCHEMA"));
        jdbcTemplate.execute(sql);
    }

    @Override
    public void dropSchema(String schemaName) {
        capabilityPolicy.assertSchemaEnabled();
        String sql = "DROP SCHEMA IF EXISTS " + quoteSchema(schemaName) + " CASCADE";
        capabilityPolicy.assertSqlAllowed(sql, java.util.Set.of("DROP SCHEMA"));
        jdbcTemplate.execute(sql);
    }

    @Override
    public List<String> listSchemas() {
        capabilityPolicy.assertSchemaEnabled();
        return jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME",
                String.class
        );
    }

    private String quoteSchema(String schemaName) {
        return domainService.quoteIdentifier(schemaName, "数据库名");
    }
}