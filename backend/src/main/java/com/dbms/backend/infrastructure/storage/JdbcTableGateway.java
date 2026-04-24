package com.dbms.backend.infrastructure.storage;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.spi.TableGateway;
import com.dbms.backend.dto.ColumnDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class JdbcTableGateway implements TableGateway {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\([0-9 ,]+\\))?$");

    private final DatabaseDomainService domainService;
    private final JdbcTemplate jdbcTemplate;

    public JdbcTableGateway(DatabaseDomainService domainService, JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createTable(String schemaName, String tableName, List<ColumnDefinition> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("列定义不能为空");
        }

        List<String> columnSql = new ArrayList<>();
        for (ColumnDefinition column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("列定义不能为空对象");
            }
            String quotedName = domainService.quoteIdentifier(column.getName(), "列名");
            String type = normalizeType(column.getType());
            boolean nullable = column.getNullable() == null || column.getNullable();
            columnSql.add(quotedName + " " + type + (nullable ? "" : " NOT NULL"));
        }

        String sql = "CREATE TABLE IF NOT EXISTS " + qualifiedTable(schemaName, tableName)
                + " (" + String.join(", ", columnSql) + ")";
        jdbcTemplate.execute(sql);
    }

    @Override
    public void dropTable(String schemaName, String tableName) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + qualifiedTable(schemaName, tableName));
    }

    @Override
    public List<String> listTables(String schemaName) {
        return jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME",
                String.class,
                schemaName.toUpperCase()
        );
    }

    private String qualifiedTable(String schemaName, String tableName) {
        return domainService.quoteIdentifier(schemaName, "数据库名") + "."
                + domainService.quoteIdentifier(tableName, "表名");
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("列类型不能为空");
        }
        String normalized = type.trim().toUpperCase();
        if (!TYPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("列类型不合法: " + type);
        }
        return normalized;
    }
}
