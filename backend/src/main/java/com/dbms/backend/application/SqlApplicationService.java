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

/**
 * SQL 执行应用服务，负责接收并执行用户提交的 SQL 语句。
 * <p>
 * 支持将 MySQL 风格的 SQL 语法转换为底层 H2 数据库兼容的语法，
 * 同时对执行的 SQL 进行安全策略校验，防止危险操作。
 * 返回结果分为"消息型"和"表格型"两种格式。
 * </p>
 *
 * @author DBMS Team
 */
@Service
public class SqlApplicationService {

    /** 允许执行的 SQL 语句前缀白名单 */
    private static final Set<String> ALLOWED_PREFIX = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TRUNCATE", "MERGE", "CALL",
            "CREATE SCHEMA", "DROP SCHEMA", "SET SCHEMA" 
    );

    /** 数据库领域服务，用于名称校验和规范化 */
    private final DatabaseDomainService domainService;

    /** 引擎能力策略，用于校验 SQL 语句是否被允许执行 */
    private final EngineCapabilityPolicy capabilityPolicy;

    /** 数据源，用于获取数据库连接 */
    private final DataSource dataSource;

    /**
     * 构造方法。
     *
     * @param domainService    数据库领域服务
     * @param capabilityPolicy 引擎能力策略
     * @param dataSource       数据源
     */
    public SqlApplicationService(DatabaseDomainService domainService,
                                 EngineCapabilityPolicy capabilityPolicy,
                                 DataSource dataSource) {
        this.domainService = domainService;
        this.capabilityPolicy = capabilityPolicy;
        this.dataSource = dataSource;
    }

    /**
     * 将 MySQL 风格的 SQL 语句转换为 H2 数据库兼容的语法。
     * <p>
     * 目前支持以下转换：
     * <ul>
     *   <li>CREATE DATABASE → CREATE SCHEMA IF NOT EXISTS</li>
     *   <li>DROP DATABASE → DROP SCHEMA IF EXISTS ... CASCADE</li>
     *   <li>SHOW DATABASES → SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA</li>
     *   <li>USE database → SET SCHEMA "database"</li>
     * </ul>
     * </p>
     *
     * @param sql 原始 SQL 语句
     * @return 转换后的 H2 兼容 SQL 语句
     */
    private String translateMySqlToH2(String sql) {
        String upper = sql.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE DATABASE")) {
            String dbName = sql.substring(upper.indexOf("DATABASE") + "DATABASE".length()).trim();
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
        if (upper.startsWith("USE ")) {
            String dbName = sql.substring(3).trim();
            if (dbName.endsWith(";")) dbName = dbName.substring(0, dbName.length() - 1);
            dbName = dbName.trim();
            return "SET SCHEMA " + domainService.quoteIdentifier(dbName, "数据库名");
        }
        return sql;
    }

    /**
     * 执行 SQL 语句。
     * <p>
     * 执行流程：MySQL 语法转换 → 安全策略校验 → 设置当前 Schema → 执行 SQL → 返回结果。
     * </p>
     *
     * @param databaseName 数据库名称（可为空）
     * @param sql          SQL 语句
     * @return 执行结果，包含类型（message 或 table）、状态、数据和影响行数等信息
     * @throws IllegalArgumentException 如果 SQL 为空
     * @throws IllegalStateException    如果 SQL 执行失败
     */
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
                String standardized = domainService.normalizeDatabaseName(normalizedDb);
                statement.execute("SET SCHEMA " + domainService.quoteIdentifier(standardized, "数据库名"));
            }

            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                int affectedRows = Math.max(statement.getUpdateCount(), 0);
                return buildMessagePayload("SQL 执行成功", affectedRows, sql);
            }

            try (ResultSet rs = statement.getResultSet()) {
                return buildTablePayload(rs);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("SQL 执行失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 构建消息类型的执行结果。
     * <p>
     * 对于 DDL 操作，会标记需要前端刷新树结构。
     * </p>
     *
     * @param message      执行消息
     * @param affectedRows 影响行数
     * @param sql          原始 SQL 语句
     * @return 消息类型的响应 Map
     */
    private Map<String, Object> buildMessagePayload(String message, int affectedRows, String sql) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message");
        payload.put("status", "success");
        payload.put("data", message);
        payload.put("affectedRows", affectedRows);
        
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("CREATE DATABASE") || upper.startsWith("DROP DATABASE") || 
            upper.startsWith("CREATE TABLE") || upper.startsWith("DROP TABLE")) {
             payload.put("refreshTree", true);
        } else {
             payload.put("refreshTree", false);
        }
        
        return payload;
    }

    /**
     * 构建表格类型的执行结果。
     * <p>
     * 将 JDBC ResultSet 转换为包含列定义和行数据的 Map 结构。
     * </p>
     *
     * @param rs JDBC 查询结果集
     * @return 表格类型的响应 Map
     * @throws Exception 如果读取结果集失败
     */
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