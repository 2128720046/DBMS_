package com.dbms.backend.infrastructure.storage;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.EngineCapabilityPolicy;
import com.dbms.backend.domain.spi.TableGateway;
import com.dbms.backend.dto.ColumnDefinition;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JdbcTableGateway implements TableGateway {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\([0-9 ,]+\\))?$");

    private final DatabaseDomainService domainService;
    private final EngineCapabilityPolicy capabilityPolicy;
    private final JdbcTemplate jdbcTemplate;

    public JdbcTableGateway(DatabaseDomainService domainService,
                            EngineCapabilityPolicy capabilityPolicy,
                            JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.capabilityPolicy = capabilityPolicy;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createTable(String schemaName, String tableName, List<ColumnDefinition> columns) {
        capabilityPolicy.assertTableEnabled();
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
        capabilityPolicy.assertSqlAllowed(sql, java.util.Set.of("CREATE TABLE"));
        jdbcTemplate.execute(sql);
    }

    @Override
    public void dropTable(String schemaName, String tableName) {
        capabilityPolicy.assertTableEnabled();
        String sql = "DROP TABLE IF EXISTS " + qualifiedTable(schemaName, tableName);
        capabilityPolicy.assertSqlAllowed(sql, java.util.Set.of("DROP TABLE"));
        jdbcTemplate.execute(sql);
    }

    @Override
    public List<String> listTables(String schemaName) {
        capabilityPolicy.assertTableEnabled();
        return jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME",
                String.class,
                schemaName.toUpperCase()
        );
    }

    @Override
    public Map<String, Object> getTableDetail(String schemaName, String tableName) {
        capabilityPolicy.assertTableEnabled();
        return jdbcTemplate.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            String schemaUpper = schemaName.toUpperCase();
            String tableUpper = tableName.toUpperCase();
            DatabaseMetaData metaData = connection.getMetaData();

            Set<String> pkColumns = new HashSet<>();
            try (ResultSet pkRs = metaData.getPrimaryKeys(null, schemaUpper, tableUpper)) {
                while (pkRs.next()) {
                    pkColumns.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            List<Map<String, Object>> columns = new ArrayList<>();
            List<String> ddlColumns = new ArrayList<>();
            try (ResultSet columnsRs = metaData.getColumns(null, schemaUpper, tableUpper, null)) {
                while (columnsRs.next()) {
                    String columnName = columnsRs.getString("COLUMN_NAME");
                    String typeName = columnsRs.getString("TYPE_NAME");
                    int size = columnsRs.getInt("COLUMN_SIZE");
                    int nullable = columnsRs.getInt("NULLABLE");
                    String defaultValue = columnsRs.getString("COLUMN_DEF");
                    boolean pk = pkColumns.contains(columnName);
                    boolean nn = nullable == DatabaseMetaData.columnNoNulls || pk;

                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("name", columnName);
                    column.put("type", renderType(typeName, size));
                    column.put("key", pk ? "PRI" : "");
                    column.put("nn", nn);
                    column.put("default", defaultValue == null ? "" : defaultValue);
                    columns.add(column);

                    String ddlPart = domainService.quoteIdentifier(columnName, "列名") + " " + renderType(typeName, size);
                    if (nn) {
                        ddlPart += " NOT NULL";
                    }
                    if (defaultValue != null && !defaultValue.isBlank()) {
                        ddlPart += " DEFAULT " + defaultValue;
                    }
                    ddlColumns.add(ddlPart);
                }
            }

            List<Map<String, Object>> indexes = new ArrayList<>();
            Set<String> indexDedup = new HashSet<>();
            try (ResultSet idxRs = metaData.getIndexInfo(null, schemaUpper, tableUpper, false, false)) {
                while (idxRs.next()) {
                    String idxName = idxRs.getString("INDEX_NAME");
                    String idxColumn = idxRs.getString("COLUMN_NAME");
                    if (idxName == null || idxColumn == null) {
                        continue;
                    }
                    String dedupKey = idxName + "#" + idxColumn;
                    if (!indexDedup.add(dedupKey)) {
                        continue;
                    }
                    Map<String, Object> index = new LinkedHashMap<>();
                    index.put("name", idxName);
                    index.put("column", idxColumn);
                    indexes.add(index);
                }
            }

            List<Map<String, Object>> fks = new ArrayList<>();
            try (ResultSet fkRs = metaData.getImportedKeys(null, schemaUpper, tableUpper)) {
                while (fkRs.next()) {
                    Map<String, Object> fk = new LinkedHashMap<>();
                    fk.put("name", fkRs.getString("FK_NAME"));
                    fk.put("column", fkRs.getString("FKCOLUMN_NAME"));
                    fk.put("refTable", fkRs.getString("PKTABLE_NAME"));
                    fk.put("refColumn", fkRs.getString("PKCOLUMN_NAME"));
                    fks.add(fk);
                }
            }

            String ddl = "CREATE TABLE " + qualifiedTable(schemaName, tableName)
                    + " (" + String.join(", ", ddlColumns)
                    + (pkColumns.isEmpty() ? "" : ", PRIMARY KEY (" + String.join(", ", pkColumns.stream().map(c -> domainService.quoteIdentifier(c, "列名")).toList()) + ")")
                    + ")";

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("columns", columns);
            detail.put("constraints", List.of());
            detail.put("fks", fks);
            detail.put("indexes", indexes);
            detail.put("ddl", ddl);
            return detail;
        });
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

    private String renderType(String typeName, int size) {
        if (typeName == null || typeName.isBlank()) {
            return "VARCHAR(255)";
        }
        String normalized = typeName.toUpperCase();
        if (("VARCHAR".equals(normalized) || "CHARACTER VARYING".equals(normalized) || "CHAR".equals(normalized)) && size > 0) {
            return "VARCHAR(" + size + ")";
        }
        return normalized;
    }
}
