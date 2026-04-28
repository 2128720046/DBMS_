package com.dbms.backend.infrastructure.storage;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.EngineCapabilityPolicy;
import com.dbms.backend.domain.spi.RecordGateway;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JdbcRecordGateway implements RecordGateway {

    private final DatabaseDomainService domainService;
    private final EngineCapabilityPolicy capabilityPolicy;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcRecordGateway(DatabaseDomainService domainService,
                             EngineCapabilityPolicy capabilityPolicy,
                             NamedParameterJdbcTemplate namedJdbc) {
        this.domainService = domainService;
        this.capabilityPolicy = capabilityPolicy;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public int insert(String schemaName, String tableName, Map<String, Object> values) {
        capabilityPolicy.assertRecordEnabled();
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("插入数据不能为空");
        }

        List<String> columns = new ArrayList<>();
        List<String> params = new ArrayList<>();
        MapSqlParameterSource source = new MapSqlParameterSource();

        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String column = domainService.quoteIdentifier(entry.getKey(), "列名");
            String param = "v" + i++;
            columns.add(column);
            params.add(":" + param);
            source.addValue(param, entry.getValue());
        }

        String sql = "INSERT INTO " + qualifiedTable(schemaName, tableName)
                + " (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", params) + ")";
        return namedJdbc.update(sql, source);
    }

    @Override
    public List<Map<String, Object>> query(String schemaName, String tableName, Map<String, Object> filters, int limit, int offset) {
        capabilityPolicy.assertRecordEnabled();
        MapSqlParameterSource source = new MapSqlParameterSource();
        String where = buildWhereClause(filters, source, "f");

        source.addValue("limit", limit);
        source.addValue("offset", offset);

        String sql = "SELECT * FROM " + qualifiedTable(schemaName, tableName)
                + where + " LIMIT :limit OFFSET :offset";
        return namedJdbc.queryForList(sql, source);
    }

    @Override
    public int update(String schemaName, String tableName, Map<String, Object> filters, Map<String, Object> values) {
        capabilityPolicy.assertRecordEnabled();
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("更新数据不能为空");
        }
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException("更新操作必须提供过滤条件");
        }

        MapSqlParameterSource source = new MapSqlParameterSource();
        List<String> setClause = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String param = "u" + i++;
            setClause.add(domainService.quoteIdentifier(entry.getKey(), "列名") + " = :" + param);
            source.addValue(param, entry.getValue());
        }

        String where = buildWhereClause(filters, source, "f");
        String sql = "UPDATE " + qualifiedTable(schemaName, tableName)
                + " SET " + String.join(", ", setClause) + where;
        return namedJdbc.update(sql, source);
    }

    @Override
    public int delete(String schemaName, String tableName, Map<String, Object> filters) {
        capabilityPolicy.assertRecordEnabled();
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException("删除操作必须提供过滤条件");
        }

        MapSqlParameterSource source = new MapSqlParameterSource();
        String where = buildWhereClause(filters, source, "f");
        String sql = "DELETE FROM " + qualifiedTable(schemaName, tableName) + where;
        return namedJdbc.update(sql, source);
    }

    private String qualifiedTable(String schemaName, String tableName) {
        return domainService.quoteIdentifier(schemaName, "数据库名") + "."
                + domainService.quoteIdentifier(tableName, "表名");
    }

    private String buildWhereClause(Map<String, Object> filters, MapSqlParameterSource source, String prefix) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String param = prefix + i++;
            clauses.add(domainService.quoteIdentifier(entry.getKey(), "过滤列") + " = :" + param);
            source.addValue(param, entry.getValue());
        }
        return " WHERE " + String.join(" AND ", clauses);
    }
}
