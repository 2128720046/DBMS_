package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.EngineCapabilityPolicy;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.model.DatabaseInfo;
import com.dbms.backend.model.TableInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 执行应用服务，负责接收并执行用户提交的 SQL 语句。
 * <p>
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
            "SHOW", "USE", "CREATE SCHEMA", "DROP SCHEMA", "SET SCHEMA"
    );

    /** 数据库领域服务，用于名称校验和规范化 */
    private final DatabaseDomainService domainService;

    /** 引擎能力策略，用于校验 SQL 语句是否被允许执行 */
    private final EngineCapabilityPolicy capabilityPolicy;

    private final DatabaseApplicationService databaseApplicationService;

    private final TableApplicationService tableApplicationService;

    private final RecordApplicationService recordApplicationService;

    private static final Pattern CREATE_DATABASE_PATTERN = Pattern.compile("(?i)^\\s*CREATE\\s+DATABASE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;?\\s*$");
    private static final Pattern DROP_DATABASE_PATTERN = Pattern.compile("(?i)^\\s*DROP\\s+DATABASE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;?\\s*$");
    private static final Pattern USE_DATABASE_PATTERN = Pattern.compile("(?i)^\\s*USE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;?\\s*$");
    private static final Pattern SHOW_DATABASES_PATTERN = Pattern.compile("(?i)^\\s*SHOW\\s+DATABASES\\s*;?\\s*$");
    private static final Pattern SHOW_TABLES_PATTERN = Pattern.compile("(?i)^\\s*SHOW\\s+TABLES\\s*;?\\s*$");
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile("(?i)^\\s*DROP\\s+TABLE(?:\\s+IF\\s+EXISTS)?\\s+([A-Za-z0-9_`\".]+)\\s*;?\\s*$");
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([A-Za-z0-9_`\".]+)\\s*\\((.*)\\)\\s*;?\\s*$");
    private static final Pattern INSERT_WITH_COLUMNS_PATTERN = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+([A-Za-z0-9_`\".]+)\\s*\\(([^)]*)\\)\\s*VALUE(?:S)?\\s*\\(([^)]*)\\)\\s*;?\\s*$");
    private static final Pattern INSERT_WITHOUT_COLUMNS_PATTERN = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+([A-Za-z0-9_`\".]+)\\s*VALUE(?:S)?\\s*\\(([^)]*)\\)\\s*;?\\s*$");
    private static final Pattern SELECT_PATTERN = Pattern.compile("(?is)^\\s*SELECT\\s+\\*\\s+FROM\\s+([A-Za-z0-9_`\".]+)(?:\\s+WHERE\\s+(.+?))?(?:\\s+LIMIT\\s+(\\d+))?(?:\\s+OFFSET\\s+(\\d+))?\\s*;?\\s*$");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("(?is)^\\s*UPDATE\\s+([A-Za-z0-9_`\".]+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?\\s*;?\\s*$");
    private static final Pattern DELETE_PATTERN = Pattern.compile("(?is)^\\s*DELETE\\s+FROM\\s+([A-Za-z0-9_`\".]+)(?:\\s+WHERE\\s+(.+))?\\s*;?\\s*$");

    /**
     * 构造方法。
     *
     * @param domainService    数据库领域服务
     * @param capabilityPolicy 引擎能力策略
     * @param dataSource       数据源
     */
    public SqlApplicationService(DatabaseDomainService domainService,
                                 EngineCapabilityPolicy capabilityPolicy,
                                 DatabaseApplicationService databaseApplicationService,
                                 TableApplicationService tableApplicationService,
                                 RecordApplicationService recordApplicationService) {
        this.domainService = domainService;
        this.capabilityPolicy = capabilityPolicy;
        this.databaseApplicationService = databaseApplicationService;
        this.tableApplicationService = tableApplicationService;
        this.recordApplicationService = recordApplicationService;
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

        String currentDb = null;
        if (databaseName != null && !databaseName.isBlank()) {
            domainService.validateDatabaseName(databaseName);
            currentDb = domainService.normalizeDatabaseName(databaseName);
        }

        List<String> statements = splitStatements(sql);
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        Map<String, Object> lastPayload = new LinkedHashMap<>();
        boolean refreshTree = false;
        int totalAffectedRows = 0;

        for (String statement : statements) {
            capabilityPolicy.assertSqlAllowed(statement, ALLOWED_PREFIX);
            ExecutionResult result = executeSingleStatement(currentDb, statement);
            currentDb = result.currentDatabase;
            refreshTree = refreshTree || result.refreshTree;
            totalAffectedRows += result.affectedRows;
            lastPayload = result.payload;
        }

        if (statements.size() > 1 && "message".equals(lastPayload.get("type"))) {
            lastPayload.put("data", "批量执行完成，共 " + statements.size() + " 条语句");
            lastPayload.put("affectedRows", totalAffectedRows);
        }
        lastPayload.put("refreshTree", refreshTree);
        if (currentDb != null) {
            lastPayload.put("selectedDatabase", currentDb);
        }
        return lastPayload;
    }

    private ExecutionResult executeSingleStatement(String currentDb, String sql) {
        Matcher matcher;

        matcher = CREATE_DATABASE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            String dbName = domainService.normalizeDatabaseName(cleanIdentifier(matcher.group(1)));
            databaseApplicationService.createDatabase(dbName);
            return ExecutionResult.message(buildMessagePayload("数据库创建成功", 1), currentDb, true, 1);
        }

        matcher = DROP_DATABASE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            String dbName = domainService.normalizeDatabaseName(cleanIdentifier(matcher.group(1)));
            databaseApplicationService.dropDatabase(dbName);
            String nextDb = dbName.equalsIgnoreCase(currentDb == null ? "" : currentDb) ? null : currentDb;
            return ExecutionResult.message(buildMessagePayload("数据库删除成功", 1), nextDb, true, 1);
        }

        matcher = USE_DATABASE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            String dbName = domainService.normalizeDatabaseName(cleanIdentifier(matcher.group(1)));
            return ExecutionResult.message(buildMessagePayload("已切换数据库: " + dbName, 0), dbName, false, 0);
        }

        if (SHOW_DATABASES_PATTERN.matcher(sql).matches()) {
            List<DatabaseInfo> dbs = databaseApplicationService.listDatabases();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (DatabaseInfo db : dbs) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("SCHEMA_NAME", db.getName());
                rows.add(row);
            }
            return ExecutionResult.table(buildTablePayload(rows), currentDb, false);
        }

        if (SHOW_TABLES_PATTERN.matcher(sql).matches()) {
            String schema = requireCurrentSchema(currentDb);
            List<TableInfo> tables = tableApplicationService.listTables(schema);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (TableInfo table : tables) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("TABLE_NAME", table.getName());
                rows.add(row);
            }
            return ExecutionResult.table(buildTablePayload(rows), currentDb, false);
        }

        matcher = CREATE_TABLE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            List<ColumnDefinition> columns = parseColumnDefinitions(matcher.group(2));
            tableApplicationService.createTable(target.databaseName, target.tableName, columns);
            return ExecutionResult.message(buildMessagePayload("数据表创建成功", 1), currentDb, true, 1);
        }

        matcher = DROP_TABLE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            tableApplicationService.dropTable(target.databaseName, target.tableName);
            return ExecutionResult.message(buildMessagePayload("数据表删除成功", 1), currentDb, true, 1);
        }

        matcher = INSERT_WITH_COLUMNS_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            List<String> columns = splitTopLevel(matcher.group(2), ',');
            List<String> values = splitTopLevel(matcher.group(3), ',');
            if (columns.size() != values.size()) {
                throw new IllegalArgumentException("INSERT 字段数和值数量不一致");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String normalizedColumn = domainService.normalizeIdentifier(cleanIdentifier(columns.get(i)));
                payload.put(normalizedColumn, parseLiteral(values.get(i)));
            }
            int affectedRows = recordApplicationService.insert(target.databaseName, target.tableName, payload);
            return ExecutionResult.message(buildMessagePayload("插入成功", affectedRows), currentDb, false, affectedRows);
        }

        matcher = INSERT_WITHOUT_COLUMNS_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            List<String> values = splitTopLevel(matcher.group(2), ',');
            Map<String, Object> tableDetail = tableApplicationService.getTableDetail(target.databaseName, target.tableName);
            List<Map<String, Object>> columnsMeta = (List<Map<String, Object>>) tableDetail.getOrDefault("columns", List.of());
            if (columnsMeta.isEmpty()) {
                throw new IllegalArgumentException("无法获取表字段定义，不能省略 INSERT 字段列表");
            }
            if (columnsMeta.size() != values.size()) {
                throw new IllegalArgumentException("INSERT 值数量与表字段数不一致");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            for (int i = 0; i < columnsMeta.size(); i++) {
                Object columnName = columnsMeta.get(i).get("name");
                if (columnName == null) {
                    continue;
                }
                payload.put(String.valueOf(columnName), parseLiteral(values.get(i)));
            }
            int affectedRows = recordApplicationService.insert(target.databaseName, target.tableName, payload);
            return ExecutionResult.message(buildMessagePayload("插入成功", affectedRows), currentDb, false, affectedRows);
        }

        matcher = SELECT_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            Map<String, Object> filters = parseWhereClause(matcher.group(2));
            Integer limit = matcher.group(3) == null ? 50 : Integer.parseInt(matcher.group(3));
            Integer offset = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) recordApplicationService.query(target.databaseName, target.tableName, filters, limit, offset).get("list");
            return ExecutionResult.table(buildTablePayload(rows), currentDb, false);
        }

        matcher = UPDATE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            Map<String, Object> setValues = parseAssignments(matcher.group(2), ',');
            Map<String, Object> filters = parseWhereClause(matcher.group(3));
            int affectedRows = recordApplicationService.update(target.databaseName, target.tableName, filters, setValues);
            return ExecutionResult.message(buildMessagePayload("更新成功", affectedRows), currentDb, false, affectedRows);
        }

        matcher = DELETE_PATTERN.matcher(sql);
        if (matcher.matches()) {
            Target target = parseTarget(requireCurrentSchema(currentDb), matcher.group(1));
            Map<String, Object> filters = parseWhereClause(matcher.group(2));
            if (filters.isEmpty()) {
                throw new IllegalArgumentException("DELETE 语句必须包含 WHERE 条件");
            }
            int affectedRows = recordApplicationService.delete(target.databaseName, target.tableName, filters);
            return ExecutionResult.message(buildMessagePayload("删除成功", affectedRows), currentDb, false, affectedRows);
        }

        throw new IllegalArgumentException("当前 SQL 不受支持，请使用 CREATE/DROP/USE/SHOW/SELECT/INSERT/UPDATE/DELETE");
    }

    private String requireCurrentSchema(String currentDb) {
        if (currentDb == null || currentDb.isBlank()) {
            throw new IllegalArgumentException("未选择数据库，请先 USE 库名 或从页面选择数据库");
        }
        return currentDb;
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = splitTopLevel(sql, ';');
        List<String> filtered = new ArrayList<>();
        for (String statement : statements) {
            if (statement != null && !statement.trim().isEmpty()) {
                filtered.add(statement.trim());
            }
        }
        return filtered;
    }

    private Target parseTarget(String fallbackDb, String rawTarget) {
        String target = cleanIdentifier(rawTarget);
        String[] parts = target.split("\\.");
        if (parts.length == 2) {
            return new Target(domainService.normalizeDatabaseName(cleanIdentifier(parts[0])), domainService.normalizeIdentifier(cleanIdentifier(parts[1])));
        }
        if (parts.length == 1) {
            return new Target(fallbackDb, domainService.normalizeIdentifier(cleanIdentifier(parts[0])));
        }
        throw new IllegalArgumentException("表名格式不合法: " + rawTarget);
    }

    private List<ColumnDefinition> parseColumnDefinitions(String rawColumns) {
        List<String> definitions = splitTopLevel(rawColumns, ',');
        List<ColumnDefinition> columns = new ArrayList<>();
        List<String> tablePrimaryKeys = new ArrayList<>();

        for (String definition : definitions) {
            String trimmed = definition.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("PRIMARY KEY")) {
                int start = trimmed.indexOf('(');
                int end = trimmed.lastIndexOf(')');
                if (start > 0 && end > start) {
                    List<String> keys = splitTopLevel(trimmed.substring(start + 1, end), ',');
                    for (String key : keys) {
                        tablePrimaryKeys.add(domainService.normalizeIdentifier(cleanIdentifier(key)));
                    }
                }
                continue;
            }

            String[] tokens = trimmed.split("\\s+", 3);
            if (tokens.length < 2) {
                throw new IllegalArgumentException("列定义不合法: " + trimmed);
            }
            String columnName = domainService.normalizeIdentifier(cleanIdentifier(tokens[0]));
            String typeToken = tokens[1].toUpperCase(Locale.ROOT);
            String constraints = tokens.length > 2 ? tokens[2].toUpperCase(Locale.ROOT) : "";

            ColumnDefinition column = new ColumnDefinition();
            column.setName(columnName);

            Matcher lengthMatcher = Pattern.compile("^([A-Z]+)\\((\\d+)(?:,\\d+)?\\)$").matcher(typeToken);
            if (lengthMatcher.matches()) {
                column.setType(lengthMatcher.group(1));
                column.setLength(Integer.parseInt(lengthMatcher.group(2)));
            } else {
                column.setType(typeToken);
            }

            boolean pkInline = constraints.contains("PRIMARY KEY");
            boolean notNull = constraints.contains("NOT NULL") || pkInline;
            boolean unique = constraints.contains("UNIQUE") || pkInline;
            column.setPk(pkInline);
            column.setNullable(!notNull);
            column.setUq(unique);
            columns.add(column);
        }

        if (!tablePrimaryKeys.isEmpty()) {
            for (ColumnDefinition column : columns) {
                if (tablePrimaryKeys.contains(domainService.normalizeIdentifier(column.getName()))) {
                    column.setPk(true);
                    column.setNullable(false);
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("CREATE TABLE 至少需要一个字段");
        }
        return columns;
    }

    private Map<String, Object> parseWhereClause(String whereSegment) {
        if (whereSegment == null || whereSegment.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Object> filters = parseAssignments(whereSegment, "AND");
        return filters;
    }

    private Map<String, Object> parseAssignments(String segment, char delimiter) {
        List<String> assignments = splitTopLevel(segment, delimiter);
        return buildAssignments(assignments);
    }

    private Map<String, Object> parseAssignments(String segment, String keywordDelimiter) {
        String[] parts = segment.split("(?i)\\s+" + keywordDelimiter + "\\s+");
        return buildAssignments(Arrays.asList(parts));
    }

    private Map<String, Object> buildAssignments(List<String> assignments) {
        Map<String, Object> values = new HashMap<>();
        for (String assignment : assignments) {
            String trimmed = assignment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                throw new IllegalArgumentException("仅支持等值条件，非法片段: " + trimmed);
            }
            String key = cleanIdentifier(trimmed.substring(0, equalsIndex));
            String rawValue = trimmed.substring(equalsIndex + 1).trim();
            values.put(domainService.normalizeIdentifier(key), parseLiteral(rawValue));
        }
        return values;
    }

    private List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth = Math.max(0, depth - 1);
                }
            }

            if (ch == delimiter && depth == 0 && !inSingleQuote && !inDoubleQuote) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        parts.add(current.toString());
        return parts;
    }

    private Object parseLiteral(String token) {
        String value = token.trim();
        if (value.equalsIgnoreCase("NULL")) {
            return null;
        }
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("FALSE")) {
            return Boolean.parseBoolean(value);
        }
        if (value.matches("^-?\\d+$")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
                return Long.parseLong(value);
            }
        }
        if (value.matches("^-?\\d+\\.\\d+$")) {
            return Double.parseDouble(value);
        }
        return cleanIdentifier(value);
    }

    private String cleanIdentifier(String raw) {
        String cleaned = raw.trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if ((cleaned.startsWith("`") && cleaned.endsWith("`")) ||
            (cleaned.startsWith("\"") && cleaned.endsWith("\""))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    private Map<String, Object> buildMessagePayload(String message, int affectedRows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message");
        payload.put("status", "success");
        payload.put("data", message);
        payload.put("affectedRows", affectedRows);
        payload.put("refreshTree", false);
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
    private Map<String, Object> buildTablePayload(List<Map<String, Object>> rows) {
        List<Map<String, Object>> columns = new ArrayList<>();
        if (!rows.isEmpty()) {
            for (String label : rows.get(0).keySet()) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("prop", label);
            col.put("label", label);
            columns.add(col);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "table");
        payload.put("status", "success");
        payload.put("columns", columns);
        payload.put("data", rows);
        payload.put("affectedRows", rows.size());
        return payload;
    }

    private static final class Target {
        private final String databaseName;
        private final String tableName;

        private Target(String databaseName, String tableName) {
            this.databaseName = databaseName;
            this.tableName = tableName;
        }
    }

    private static final class ExecutionResult {
        private final Map<String, Object> payload;
        private final String currentDatabase;
        private final boolean refreshTree;
        private final int affectedRows;

        private ExecutionResult(Map<String, Object> payload, String currentDatabase, boolean refreshTree, int affectedRows) {
            this.payload = payload;
            this.currentDatabase = currentDatabase;
            this.refreshTree = refreshTree;
            this.affectedRows = affectedRows;
        }

        private static ExecutionResult message(Map<String, Object> payload, String currentDatabase, boolean refreshTree, int affectedRows) {
            return new ExecutionResult(payload, currentDatabase, refreshTree, affectedRows);
        }

        private static ExecutionResult table(Map<String, Object> payload, String currentDatabase, boolean refreshTree) {
            return new ExecutionResult(payload, currentDatabase, refreshTree, 0);
        }
    }
}