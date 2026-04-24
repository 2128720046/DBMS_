package com.dbms.backend.infrastructure.storage;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class JdbcSchemaGateway implements DatabaseSchemaGateway {

    private final DatabaseDomainService domainService;
    private final JdbcTemplate jdbcTemplate;

    public JdbcSchemaGateway(DatabaseDomainService domainService, JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + quoteSchema(schemaName));
    }

    @Override
    public void dropSchema(String schemaName) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quoteSchema(schemaName) + " CASCADE");
    }

    @Override
    public List<String> listSchemas() {
        return jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME",
                String.class
        );
    }

    private String quoteSchema(String schemaName) {
        return domainService.quoteIdentifier(schemaName, "数据库名");
    }
}