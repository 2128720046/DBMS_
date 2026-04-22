package com.dbms.backend.infrastructure.h2;

import com.dbms.backend.domain.spi.DatabaseSchemaGateway;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class H2DatabaseSchemaGateway implements DatabaseSchemaGateway {

    private final JdbcTemplate jdbcTemplate;

    public H2DatabaseSchemaGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
    }

    @Override
    public void dropSchema(String schemaName) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
    }

    @Override
    public List<String> listSchemas() {
        return jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME",
                String.class
        );
    }
}