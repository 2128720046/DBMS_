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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SqlApplicationService {

    private static final Set<String> ALLOWED_PREFIX = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TRUNCATE", "MERGE", "CALL",
            "CREATE SCHEMA", "DROP SCHEMA", "SET SCHEMA" 
    );

    private final DatabaseDomainService domainService;
    private final EngineCapabilityPolicy capabilityPolicy;
    private final DataSource dataSource;

    public SqlApplicationService(DatabaseDomainService domainService,
                                 EngineCapabilityPolicy capabilityPolicy,
                                 DataSource dataSource) {
        this.domainService = domainService;
        this.capabilityPolicy = capabilityPolicy;
        this.dataSource = dataSource;
    }

    
    // 在 SqlApplicationService 类中添加一个方法
private String translateMySqlToH2(String sql) {
    String upper = sql.trim().toUpperCase(Locale.ROOT);
    if (upper.startsWith("CREATE DATABASE")) {
        String dbName = sql.substring(upper.indexOf("DATABASE") + "DATABASE".length()).trim();
        // 去除结尾分号
        if (dbName.endsWith(";")) dbName = dbName.substring(0, dbName.length() - 1);
        dbName = dbName.trim();
        return "CREATE SCHEMA IF NOT EXISTS " + domainService.quoteIdentifier(dbName, "数据库名");
    }
    if (upper.startsWith("DROP DATABASE")) {
        String dbName = sql.substring(upper.indexOf("DATABASE") + "DATABASE".length()).trim();
        if (dbName.endsWith(";")) dbName = dbName.substring(0, dbName.length() - 1);
        dbName = dbName.trim();
        return "DROP SCHEMA IF EXISTS " + domainService.quoteIdentifier(dbName, "数据库名") + " CASCADE";
    }
    if (upper.startsWith("SHOW DATABASES")) {
        return "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA";
    }
    // 可以继续添加 USE database → SET SCHEMA "database" 等
    if (upper.startsWith("USE ")) {
        String dbName = sql.substring(3).trim();
        if (dbName.endsWith(";")) dbName = dbName.substring(0, dbName.length() - 1);
        dbName = dbName.trim();
        return "SET SCHEMA " + domainService.quoteIdentifier(dbName, "数据库名");
    }
    return sql;
}

  
    public Map<String, Object> execute(String databaseName, String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        sql = translateMySqlToH2(sql);
        capabilityPolicy.assertSqlAllowed(sql, ALLOWED_PREFIX);

        String normalizedDb = null;
        if (databaseName != null && !databaseName.isBlank()) {
            domainService.validateDatabaseName(databaseName);
            normalizedDb = databaseName;
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
