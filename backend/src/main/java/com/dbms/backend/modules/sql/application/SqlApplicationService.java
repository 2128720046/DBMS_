package com.dbms.backend.modules.sql.application;

import com.dbms.backend.core.capability.EngineCapabilityPolicy;
import com.dbms.backend.modules.sql.executor.SqlExecutor;
import com.dbms.backend.modules.sql.parser.SqlCommand;
import com.dbms.backend.modules.sql.parser.SqlParser;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQL 执行入口编排器：负责 SQL 规范化、白名单校验并交给解析与执行层。
 * 解析规则在 SqlParser，执行路由在 SqlExecutor。
 */
@Service
public class SqlApplicationService {

    /** 允许执行的 SQL 语句前缀白名单 */
    private static final Set<String> ALLOWED_PREFIX = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TRUNCATE", "MERGE", "CALL",
            "CREATE SCHEMA", "DROP SCHEMA", "SET SCHEMA", "SHOW", "USE",
            "DESCRIBE", "DESC",
            "BEGIN", "START TRANSACTION", "COMMIT", "ROLLBACK",
            "BACKUP", "RESTORE", "GRANT", "REVOKE", "CONNECT", "DISCONNECT", "REBUILD"
    );

    /** 引擎能力策略，用于校验 SQL 语句是否被允许执行 */
    private final EngineCapabilityPolicy capabilityPolicy;

    /** SQL 解析器，将 SQL 转为结构化命令 */
    private final SqlParser sqlParser;

    /** SQL 执行器，将结构化命令路由到应用服务 */
    private final SqlExecutor sqlExecutor;

    /**
     * 构造方法。
     *
     * @param domainService    数据库领域服务
     * @param capabilityPolicy 引擎能力策略
     * @param databaseApplicationService 数据库应用服务
     * @param tableApplicationService    表应用服务
     * @param recordApplicationService   记录应用服务
     */
    public SqlApplicationService(EngineCapabilityPolicy capabilityPolicy,
                                 SqlParser sqlParser,
                                 SqlExecutor sqlExecutor) {
        this.capabilityPolicy = capabilityPolicy;
        this.sqlParser = sqlParser;
        this.sqlExecutor = sqlExecutor;
    }

    /**
    * 将 MySQL 风格的 SQL 语句转换为内部解析友好的格式。
     * <p>
     * 目前支持以下转换：
     * <ul>
    *   <li>去除尾部分号</li>
     * </ul>
     * </p>
     *
     * @param sql 原始 SQL 语句
     * @return 转换后的 SQL 语句
     */
    private String normalizeSql(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * 执行 SQL 语句。
     * <p>
    * 执行流程：SQL 规范化 → 安全策略校验 → 解析路由 → 执行 → 返回结果。
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
        String normalizedSql = normalizeSql(sql);
        capabilityPolicy.assertSqlAllowed(normalizedSql, ALLOWED_PREFIX);
        SqlCommand command = sqlParser.parse(normalizedSql);
        return sqlExecutor.execute(databaseName, normalizedSql, command);
    }
}


