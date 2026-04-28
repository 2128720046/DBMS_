package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.EngineCapabilityPolicy;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlApplicationService {

    private static final Pattern CREATE_DATABASE_PATTERN = Pattern.compile(
        "^\\s*CREATE\\s+DATABASE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([\"`]?)([A-Za-z][A-Za-z0-9_]{0,31})\\1\\s*;?\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DROP_DATABASE_PATTERN = Pattern.compile(
        "^\\s*DROP\\s+DATABASE(?:\\s+IF\\s+EXISTS)?\\s+([\"`]?)([A-Za-z][A-Za-z0-9_]{0,31})\\1\\s*;?\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> ALLOWED_PREFIX = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TRUNCATE", "MERGE", "CALL"
    );

    private final DatabaseDomainService domainService;
    private final DatabaseApplicationService databaseApplicationService;
    private final EngineCapabilityPolicy capabilityPolicy;
    private final DataSource dataSource;

    public SqlApplicationService(DatabaseDomainService domainService,
                                 DatabaseApplicationService databaseApplicationService,
                                 EngineCapabilityPolicy capabilityPolicy,
                                 DataSource dataSource) {
        this.domainService = domainService;
        this.databaseApplicationService = databaseApplicationService;
        this.capabilityPolicy = capabilityPolicy;
        this.dataSource = dataSource;
    }

    public Map<String, Object> execute(String databaseName, String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        Matcher createMatcher = CREATE_DATABASE_PATTERN.matcher(sql);
        if (createMatcher.matches()) {
            String dbName = domainService.normalizeDatabaseName(createMatcher.group(2));
            databaseApplicationService.createDatabase(dbName);
            return buildMessagePayload("数据库创建成功", 0);
        }

        Matcher dropMatcher = DROP_DATABASE_PATTERN.matcher(sql);
        if (dropMatcher.matches()) {
            String dbName = domainService.normalizeDatabaseName(dropMatcher.group(2));
            databaseApplicationService.dropDatabase(dbName);
            return buildMessagePayload("数据库删除成功", 0);
        }

        capabilityPolicy.assertSqlAllowed(sql, ALLOWED_PREFIX);

        String normalizedDb = null;
        if (databaseName != null && !databaseName.isBlank()) {
            normalizedDb = domainService.normalizeDatabaseName(databaseName);
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (normalizedDb != null) {
                statement.execute("SET SCHEMA " + domainService.quoteIdentifier(normalizedDb, "数据库名"));
            }

            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                int affectedRows = Math.max(statement.getUpdateCount(), 0);
                return buildMessagePayload("SQL 执行成功", affectedRows);
            }

            try (ResultSet rs = statement.getResultSet()) {
                return buildTablePayload(rs);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("SQL 执行失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildMessagePayload(String message, int affectedRows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message");
        payload.put("status", "success");
        payload.put("data", message);
        payload.put("affectedRows", affectedRows);
        return payload;
    }

    private Map<String, Object> buildTablePayload(ResultSet rs) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            String label = meta.getColumnLabel(i);
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("prop", label);
            col.put("label", label);
            columns.add(col);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "table");
        payload.put("status", "success");
        payload.put("columns", columns);
        payload.put("data", rows);
        payload.put("affectedRows", rows.size());
        return payload;
    }
}
