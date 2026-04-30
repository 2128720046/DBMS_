package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.domain.EngineCapabilityPolicy;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.infrastructure.storage.config.StorageEngineConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQL 执行应用服务，负责接收并执行用户提交的 SQL 语句。
 * <p>
 * 支持 MySQL 风格的 SQL 语法子集，
 * 直接路由到原生二进制存储引擎执行。
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
            "CREATE SCHEMA", "DROP SCHEMA", "SET SCHEMA", "SHOW", "USE"
    );

    /** 数据库领域服务，用于名称校验和规范化 */
    private final DatabaseDomainService domainService;

    /** 引擎能力策略，用于校验 SQL 语句是否被允许执行 */
    private final EngineCapabilityPolicy capabilityPolicy;

    /** 数据库应用服务，用于数据库级别操作 */
    private final DatabaseApplicationService databaseApplicationService;

    /** 表应用服务，用于表级别操作 */
    private final TableApplicationService tableApplicationService;

    /** 记录应用服务，用于记录级别操作 */
    private final RecordApplicationService recordApplicationService;

    /**
     * 构造方法。
     *
     * @param domainService    数据库领域服务
     * @param capabilityPolicy 引擎能力策略
     * @param databaseApplicationService 数据库应用服务
     * @param tableApplicationService    表应用服务
     * @param recordApplicationService   记录应用服务
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

        String upper = normalizedSql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("SHOW DATABASES")) {
            return buildDatabaseListPayload();
        }
        if (upper.startsWith("USE ")) {
            return buildMessagePayload("已切换数据库", 0, normalizedSql);
        }

        if (upper.startsWith("CREATE DATABASE")) {
            String dbName = extractDatabaseName(normalizedSql, "CREATE DATABASE");
            databaseApplicationService.createDatabase(dbName);
            return buildMessagePayload("数据库创建成功", 1, normalizedSql);
        }
        if (upper.startsWith("DROP DATABASE")) {
            String dbName = extractDatabaseName(normalizedSql, "DROP DATABASE");
            databaseApplicationService.dropDatabase(dbName);
            return buildMessagePayload("数据库删除成功", 1, normalizedSql);
        }

        String normalizedDb = requireDatabase(databaseName);

        if (upper.startsWith("SHOW TABLES")) {
            return buildTableListPayload(normalizedDb);
        }
        if (upper.startsWith("DESCRIBE ") || upper.startsWith("DESC ")) {
            String tableName = extractSingleIdentifier(normalizedSql, upper.startsWith("DESCRIBE ") ? "DESCRIBE" : "DESC");
            return buildDescribePayload(normalizedDb, tableName);
        }

        if (upper.startsWith("CREATE TABLE")) {
            CreateTableParts parts = parseCreateTable(normalizedSql);
            // 确保使用指定的databaseName，如果未指定则使用系统库
            String targetDb = databaseName != null && !databaseName.isEmpty() ? normalizedDb : StorageEngineConfig.getSystemSchemaName();
            tableApplicationService.createTable(targetDb, parts.tableName, parts.columns);
            return buildMessagePayload("表创建成功", 0, normalizedSql);
        }
        if (upper.startsWith("DROP TABLE")) {
            String tableName = extractSingleIdentifier(normalizedSql, "DROP TABLE");
            tableApplicationService.dropTable(normalizedDb, tableName);
            return buildMessagePayload("表删除成功", 0, normalizedSql);
        }
        if (upper.startsWith("ALTER TABLE")) {
            AlterTableParts parts = parseAlterTable(normalizedSql);
            applyAlterTable(normalizedDb, parts);
            return buildMessagePayload("表结构更新成功", 0, normalizedSql);
        }
        if (upper.startsWith("INSERT INTO")) {
            InsertParts parts = parseInsert(normalizedSql);
            int affected = recordApplicationService.insert(normalizedDb, parts.tableName, parts.values);
            return buildMessagePayload("插入成功", affected, normalizedSql);
        }
        if (upper.startsWith("SELECT")) {
            SelectParts parts = parseSelect(normalizedSql);
            List<Map<String, Object>> rows = fetchAllRows(normalizedDb, parts.tableName);
            rows = filterRows(rows, parts.filters);
            rows = sortRows(rows, parts.orderBy);
            rows = applyLimit(rows, parts.limit);
            List<String> columnOrder = parts.projection;
            if (columnOrder == null || columnOrder.isEmpty()) {
                columnOrder = readColumnOrder(normalizedDb, parts.tableName);
            }
            return buildTablePayloadFromRows(rows, columnOrder);
        }
        if (upper.startsWith("UPDATE")) {
            UpdateParts parts = parseUpdate(normalizedSql);
            assertSimpleFilter(parts.filters, "UPDATE");
            int affected = recordApplicationService.update(normalizedDb, parts.tableName, toEqualityMap(parts.filters), parts.values);
            return buildMessagePayload("更新成功", affected, normalizedSql);
        }
        if (upper.startsWith("DELETE FROM")) {
            DeleteParts parts = parseDelete(normalizedSql);
            assertSimpleFilter(parts.filters, "DELETE");
            int affected = recordApplicationService.delete(normalizedDb, parts.tableName, toEqualityMap(parts.filters));
            return buildMessagePayload("删除成功", affected, normalizedSql);
        }

        throw new IllegalArgumentException("暂不支持的 SQL 语句");
    }

    private String requireDatabase(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("请先指定数据库");
        }
        return domainService.normalizeDatabaseName(databaseName);
    }

    private Map<String, Object> buildDatabaseListPayload() {
        List<Map<String, Object>> rows = new ArrayList<>();
        databaseApplicationService.listDatabases().forEach(db -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SCHEMA_NAME", db.getName());
            rows.add(row);
        });
        return buildTablePayloadFromRows(rows, List.of("SCHEMA_NAME"));
    }

    private Map<String, Object> buildTableListPayload(String databaseName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        tableApplicationService.listTables(databaseName).forEach(table -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("TABLE_NAME", table.getName());
            rows.add(row);
        });
        return buildTablePayloadFromRows(rows, List.of("TABLE_NAME"));
    }

    private Map<String, Object> buildDescribePayload(String databaseName, String tableName) {
        Map<String, Object> detail = tableApplicationService.getTableDetail(databaseName, tableName);
        Object columnsObj = detail.get("columns");
        if (!(columnsObj instanceof List<?> list)) {
            return buildTablePayloadFromRows(List.of(), List.of("COLUMN", "TYPE", "NULLABLE", "PRIMARY_KEY"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("COLUMN", map.get("name"));
                row.put("TYPE", map.get("type"));
                row.put("NULLABLE", map.get("nullable"));
                row.put("PRIMARY_KEY", map.get("primaryKey"));
                rows.add(row);
            }
        }
        return buildTablePayloadFromRows(rows, List.of("COLUMN", "TYPE", "NULLABLE", "PRIMARY_KEY"));
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
              upper.startsWith("CREATE TABLE") || upper.startsWith("DROP TABLE") ||
              upper.startsWith("ALTER TABLE")) {
             payload.put("refreshTree", true);
        } else {
             payload.put("refreshTree", false);
        }
        
        return payload;
    }

    private Map<String, Object> buildTablePayloadFromRows(List<Map<String, Object>> rows, List<String> columnOrder) {
        List<String> columnsResolved = new ArrayList<>();
        if (columnOrder != null && !columnOrder.isEmpty()) {
            columnsResolved.addAll(columnOrder);
        } else if (rows != null && !rows.isEmpty()) {
            columnsResolved.addAll(rows.get(0).keySet());
        }

        List<Map<String, Object>> columns = new ArrayList<>();
        for (String label : columnsResolved) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("prop", label);
            col.put("label", label);
            columns.add(col);
        }

        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                if (!columnsResolved.isEmpty()) {
                    for (String col : columnsResolved) {
                        normalized.put(col, row.get(col));
                    }
                } else {
                    normalized.putAll(row);
                }
                normalizedRows.add(normalized);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "table");
        payload.put("status", "success");
        payload.put("columns", columns);
        payload.put("data", normalizedRows);
        payload.put("affectedRows", normalizedRows.size());
        return payload;
    }

    private List<String> readColumnOrder(String databaseName, String tableName) {
        try {
            Map<String, Object> detail = tableApplicationService.getTableDetail(databaseName, tableName);
            Object columnsObj = detail.get("columns");
            if (columnsObj instanceof List<?> list) {
                List<String> names = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object name = map.get("name");
                        if (name != null) {
                            names.add(String.valueOf(name));
                        }
                    }
                }
                return names;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private List<Map<String, Object>> fetchAllRows(String databaseName, String tableName) {
        return recordApplicationService.query(databaseName, tableName, Map.of(), 200, 0);
    }

    private String extractSingleIdentifier(String sql, String keyword) {
        String rest = sql.substring(keyword.length()).trim();
        if (rest.isEmpty()) {
            throw new IllegalArgumentException(keyword + " 缺少名称");
        }
        String token = extractIdentifierToken(rest);
        return domainService.normalizeIdentifier(token);
    }

    private String extractDatabaseName(String sql, String keyword) {
        String rest = sql.substring(keyword.length()).trim();
        if (rest.isEmpty()) {
            throw new IllegalArgumentException(keyword + " 缺少名称");
        }
        String token = extractIdentifierToken(rest);
        return domainService.normalizeDatabaseName(token);
    }

    private String extractIdentifierToken(String text) {
        String trimmed = text.trim();
        int end = trimmed.length();
        int space = trimmed.indexOf(' ');
        int paren = trimmed.indexOf('(');
        if (space != -1) {
            end = Math.min(end, space);
        }
        if (paren != -1) {
            end = Math.min(end, paren);
        }
        String token = trimmed.substring(0, end).trim();
        return stripIdentifierQuotes(token);
    }

    private String stripIdentifierQuotes(String token) {
        if ((token.startsWith("\"") && token.endsWith("\"")) ||
            (token.startsWith("`") && token.endsWith("`")) ||
            (token.startsWith("[") && token.endsWith("]"))) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    private CreateTableParts parseCreateTable(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int start = upper.indexOf("CREATE TABLE") + "CREATE TABLE".length();
        String rest = sql.substring(start).trim();
        if (rest.toUpperCase(Locale.ROOT).startsWith("IF NOT EXISTS")) {
            rest = rest.substring("IF NOT EXISTS".length()).trim();
        }
        int parenStart = rest.indexOf('(');
        if (parenStart == -1) {
            throw new IllegalArgumentException("CREATE TABLE 需要字段定义");
        }
        String tableName = stripIdentifierQuotes(rest.substring(0, parenStart).trim());
        tableName = domainService.normalizeIdentifier(tableName);

        int parenEnd = findMatchingParen(rest, parenStart);
        String columnsPart = rest.substring(parenStart + 1, parenEnd).trim();
        if (columnsPart.isEmpty()) {
            throw new IllegalArgumentException("CREATE TABLE 需要字段定义");
        }
        List<String> columnDefs = splitTopLevel(columnsPart, ',');
        List<ColumnDefinition> columns = new ArrayList<>();
        for (String def : columnDefs) {
            ColumnDefinition column = parseColumnDefinition(def.trim());
            columns.add(column);
        }
        return new CreateTableParts(tableName, columns);
    }

    private ColumnDefinition parseColumnDefinition(String def) {
        String[] tokens = def.trim().split("\\s+");
        if (tokens.length < 2) {
            throw new IllegalArgumentException("字段定义不完整: " + def);
        }
        String name = stripIdentifierQuotes(tokens[0]);
        name = domainService.normalizeIdentifier(name);

        String typeToken = tokens[1].toUpperCase(Locale.ROOT);
        String baseType = typeToken;
        Integer length = null;
        int idx = typeToken.indexOf('(');
        if (idx > 0 && typeToken.endsWith(")")) {
            baseType = typeToken.substring(0, idx);
            String lenStr = typeToken.substring(idx + 1, typeToken.length() - 1).trim();
            if (!lenStr.isEmpty()) {
                length = Integer.parseInt(lenStr);
            }
        }

        String upper = def.toUpperCase(Locale.ROOT);
        ColumnDefinition column = new ColumnDefinition();
        column.setName(name);
        column.setType(baseType);
        column.setLength(length);
        if (upper.contains("PRIMARY KEY")) {
            column.setPk(true);
            column.setNullable(false);
        }
        if (upper.contains("NOT NULL")) {
            column.setNullable(false);
        }
        if (upper.contains("UNIQUE")) {
            column.setUq(true);
        }
        return column;
    }

    private InsertParts parseInsert(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int intoIndex = upper.indexOf("INSERT INTO") + "INSERT INTO".length();
        String rest = sql.substring(intoIndex).trim();
        String tableName = extractIdentifierToken(rest);
        tableName = domainService.normalizeIdentifier(tableName);

        if (rest.indexOf('(') == -1) {
            throw new IllegalArgumentException("INSERT INTO 需要字段列表");
        }
        int columnsStart = rest.indexOf('(');
        int columnsEnd = findMatchingParen(rest, columnsStart);
        String columnsPart = rest.substring(columnsStart + 1, columnsEnd).trim();
        List<String> columns = splitTopLevel(columnsPart, ',');

        int valuesIndex = upper.indexOf("VALUES", intoIndex);
        if (valuesIndex == -1) {
            throw new IllegalArgumentException("INSERT INTO 缺少 VALUES");
        }
        String valuesRest = sql.substring(valuesIndex + "VALUES".length()).trim();
        int valuesStart = valuesRest.indexOf('(');
        int valuesEnd = findMatchingParen(valuesRest, valuesStart);
        String valuesPart = valuesRest.substring(valuesStart + 1, valuesEnd).trim();
        List<String> values = splitTopLevel(valuesPart, ',');

        if (columns.size() != values.size()) {
            throw new IllegalArgumentException("INSERT INTO 字段和值数量不匹配");
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = domainService.normalizeIdentifier(stripIdentifierQuotes(columns.get(i).trim()));
            map.put(column, parseLiteral(values.get(i).trim()));
        }
        return new InsertParts(tableName, map);
    }

    private SelectParts parseSelect(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int fromIndex = upper.indexOf(" FROM ");
        if (fromIndex == -1) {
            throw new IllegalArgumentException("SELECT 缺少 FROM");
        }
        String projectionPart = sql.substring("SELECT".length(), fromIndex).trim();
        String afterFrom = sql.substring(fromIndex + " FROM ".length()).trim();
        String upperAfterFrom = afterFrom.toUpperCase(Locale.ROOT);

        int whereIndex = findKeyword(upperAfterFrom, " WHERE ");
        int orderIndex = findKeyword(upperAfterFrom, " ORDER BY ");
        int limitIndex = findKeyword(upperAfterFrom, " LIMIT ");

        int endOfTable = minPositive(whereIndex, orderIndex, limitIndex, upperAfterFrom.length());
        String tablePart = afterFrom.substring(0, endOfTable).trim();
        String tableName = domainService.normalizeIdentifier(extractIdentifierToken(tablePart));

        String wherePart = null;
        if (whereIndex != -1) {
            int start = whereIndex + " WHERE ".length();
            int end = minPositive(orderIndex, limitIndex, upperAfterFrom.length());
            wherePart = afterFrom.substring(start, end).trim();
        }

        String orderPart = null;
        if (orderIndex != -1) {
            int start = orderIndex + " ORDER BY ".length();
            int end = minPositive(limitIndex, upperAfterFrom.length());
            orderPart = afterFrom.substring(start, end).trim();
        }

        String limitPart = null;
        if (limitIndex != -1) {
            int start = limitIndex + " LIMIT ".length();
            limitPart = afterFrom.substring(start).trim();
        }

        List<String> projection = null;
        if (!projectionPart.equals("*")) {
            projection = new ArrayList<>();
            for (String item : splitTopLevel(projectionPart, ',')) {
                projection.add(domainService.normalizeIdentifier(stripIdentifierQuotes(item.trim())));
            }
        }

        FilterExpression filters = parseFilterExpression(wherePart);
        List<OrderBy> orderBy = parseOrderBy(orderPart);
        Limit limit = parseLimit(limitPart);
        return new SelectParts(tableName, projection, filters, orderBy, limit);
    }

    private UpdateParts parseUpdate(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int setIndex = upper.indexOf(" SET ");
        if (setIndex == -1) {
            throw new IllegalArgumentException("UPDATE 缺少 SET");
        }
        String tablePart = sql.substring("UPDATE".length(), setIndex).trim();
        String tableName = domainService.normalizeIdentifier(stripIdentifierQuotes(tablePart));

        String afterSet = sql.substring(setIndex + " SET ".length()).trim();
        int whereIndex = afterSet.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
        String assignmentsPart = whereIndex == -1 ? afterSet : afterSet.substring(0, whereIndex).trim();
        String wherePart = whereIndex == -1 ? null : afterSet.substring(whereIndex + " WHERE ".length()).trim();

        Map<String, Object> values = new LinkedHashMap<>();
        for (String assign : splitTopLevel(assignmentsPart, ',')) {
            String[] pair = assign.split("=", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException("UPDATE SET 赋值格式错误: " + assign);
            }
            String column = domainService.normalizeIdentifier(stripIdentifierQuotes(pair[0].trim()));
            values.put(column, parseLiteral(pair[1].trim()));
        }
        FilterExpression filters = parseFilterExpression(wherePart);
        return new UpdateParts(tableName, values, filters);
    }

    private DeleteParts parseDelete(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int fromIndex = upper.indexOf("DELETE FROM");
        String afterFrom = sql.substring(fromIndex + "DELETE FROM".length()).trim();
        String tableName = extractIdentifierToken(afterFrom);
        tableName = domainService.normalizeIdentifier(tableName);

        int whereIndex = afterFrom.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
        String wherePart = whereIndex == -1 ? null : afterFrom.substring(whereIndex + " WHERE ".length()).trim();
        FilterExpression filters = parseFilterExpression(wherePart);
        return new DeleteParts(tableName, filters);
    }

    private FilterExpression parseFilterExpression(String wherePart) {
        if (wherePart == null || wherePart.isBlank()) {
            return FilterExpression.empty();
        }
        List<List<FilterPredicate>> groups = new ArrayList<>();
        boolean hasOr = false;
        boolean hasNonEquality = false;
        for (String orPart : splitByKeyword(wherePart, "OR")) {
            List<FilterPredicate> predicates = new ArrayList<>();
            for (String andPart : splitByKeyword(orPart, "AND")) {
                FilterPredicate predicate = parsePredicate(andPart.trim());
                if (predicate.operator != Operator.EQ) {
                    hasNonEquality = true;
                }
                predicates.add(predicate);
            }
            if (!predicates.isEmpty()) {
                groups.add(predicates);
            }
        }
        if (groups.size() > 1) {
            hasOr = true;
        }
        return new FilterExpression(groups, hasOr, hasNonEquality);
    }

    private FilterPredicate parsePredicate(String clause) {
        String trimmed = clause.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件为空");
        }
        String[] operators = new String[] {">=", "<=", "!=", "=", ">", "<"};
        for (String op : operators) {
            int idx = indexOfOperator(trimmed, op);
            if (idx != -1) {
                String left = trimmed.substring(0, idx).trim();
                String right = trimmed.substring(idx + op.length()).trim();
                if (left.isEmpty() || right.isEmpty()) {
                    break;
                }
                String column = domainService.normalizeIdentifier(stripIdentifierQuotes(left));
                Object value = parseLiteral(right);
                return new FilterPredicate(column, Operator.fromSymbol(op), value);
            }
        }
        throw new IllegalArgumentException("WHERE 条件格式不支持: " + clause);
    }

    private int indexOfOperator(String text, String operator) {
        boolean inString = false;
        for (int i = 0; i <= text.length() - operator.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (text.regionMatches(i, operator, 0, operator.length())) {
                return i;
            }
        }
        return -1;
    }

    private Object parseLiteral(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (trimmed.equalsIgnoreCase("TRUE")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("FALSE")) {
            return false;
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            return inner.replace("''", "'");
        }
        if (trimmed.contains(".")) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
        }
        return trimmed;
    }

    private List<Map<String, Object>> filterRows(List<Map<String, Object>> rows, FilterExpression expression) {
        if (expression.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (matchesExpression(row, expression)) {
                results.add(row);
            }
        }
        return results;
    }

    private boolean matchesExpression(Map<String, Object> row, FilterExpression expression) {
        for (List<FilterPredicate> group : expression.groups) {
            boolean groupMatch = true;
            for (FilterPredicate predicate : group) {
                if (!matchesPredicate(row, predicate)) {
                    groupMatch = false;
                    break;
                }
            }
            if (groupMatch) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPredicate(Map<String, Object> row, FilterPredicate predicate) {
        Object value = row.get(predicate.column);
        Object literal = predicate.value;
        if (predicate.operator == Operator.EQ) {
            return compareValues(value, literal) == 0;
        }
        if (predicate.operator == Operator.NE) {
            return compareValues(value, literal) != 0;
        }
        int cmp = compareValues(value, literal);
        return switch (predicate.operator) {
            case GT -> cmp > 0;
            case LT -> cmp < 0;
            case GTE -> cmp >= 0;
            case LTE -> cmp <= 0;
            default -> false;
        };
    }

    private int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number && right instanceof Number) {
            double l = ((Number) left).doubleValue();
            double r = ((Number) right).doubleValue();
            return Double.compare(l, r);
        }
        if (left instanceof String && right instanceof Number) {
            try {
                double l = Double.parseDouble((String) left);
                double r = ((Number) right).doubleValue();
                return Double.compare(l, r);
            } catch (NumberFormatException ignored) {
            }
        }
        if (left instanceof Number && right instanceof String) {
            try {
                double l = ((Number) left).doubleValue();
                double r = Double.parseDouble((String) right);
                return Double.compare(l, r);
            } catch (NumberFormatException ignored) {
            }
        }
        if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> safe = (Comparable<Object>) comparable;
            return safe.compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private List<Map<String, Object>> sortRows(List<Map<String, Object>> rows, List<OrderBy> orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        Comparator<Map<String, Object>> comparator = null;
        for (OrderBy order : orderBy) {
            Comparator<Map<String, Object>> next = Comparator.comparing(row -> row.get(order.column), this::compareValuesNullSafe);
            if (!order.asc) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        if (comparator != null) {
            sorted.sort(comparator);
        }
        return sorted;
    }

    private int compareValuesNullSafe(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return compareValues(left, right);
    }

    private List<Map<String, Object>> applyLimit(List<Map<String, Object>> rows, Limit limit) {
        if (limit == null) {
            return rows;
        }
        int offset = Math.max(limit.offset, 0);
        int end = limit.size < 0 ? rows.size() : Math.min(rows.size(), offset + limit.size);
        if (offset >= rows.size()) {
            return List.of();
        }
        return new ArrayList<>(rows.subList(offset, end));
    }

    private List<OrderBy> parseOrderBy(String orderPart) {
        if (orderPart == null || orderPart.isBlank()) {
            return List.of();
        }
        List<OrderBy> orders = new ArrayList<>();
        for (String item : splitTopLevel(orderPart, ',')) {
            String[] tokens = item.trim().split("\\s+");
            if (tokens.length == 0) {
                continue;
            }
            String column = domainService.normalizeIdentifier(stripIdentifierQuotes(tokens[0]));
            boolean asc = true;
            if (tokens.length > 1) {
                asc = !tokens[1].equalsIgnoreCase("DESC");
            }
            orders.add(new OrderBy(column, asc));
        }
        return orders;
    }

    private Limit parseLimit(String limitPart) {
        if (limitPart == null || limitPart.isBlank()) {
            return null;
        }
        String[] tokens = limitPart.split(",");
        if (tokens.length == 1) {
            int size = Integer.parseInt(tokens[0].trim());
            return new Limit(0, size);
        }
        int offset = Integer.parseInt(tokens[0].trim());
        int size = Integer.parseInt(tokens[1].trim());
        return new Limit(offset, size);
    }

    private void assertSimpleFilter(FilterExpression expression, String action) {
        if (expression.hasOr || expression.hasNonEquality) {
            throw new IllegalArgumentException(action + " 仅支持 AND 等值条件");
        }
    }

    private Map<String, Object> toEqualityMap(FilterExpression expression) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (expression.isEmpty()) {
            return filters;
        }
        for (FilterPredicate predicate : expression.groups.get(0)) {
            filters.put(predicate.column, predicate.value);
        }
        return filters;
    }

    private AlterTableParts parseAlterTable(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int start = upper.indexOf("ALTER TABLE") + "ALTER TABLE".length();
        String rest = sql.substring(start).trim();
        String rawTableToken = extractIdentifierToken(rest);
        String tableName = domainService.normalizeIdentifier(rawTableToken);
        int tokenIndex = rest.toUpperCase(Locale.ROOT).indexOf(rawTableToken.toUpperCase(Locale.ROOT));
        if (tokenIndex == -1) {
            throw new IllegalArgumentException("ALTER TABLE 表名解析失败");
        }
        String afterTable = rest.substring(tokenIndex + rawTableToken.length()).trim();
        if (afterTable.isEmpty()) {
            throw new IllegalArgumentException("ALTER TABLE 缺少操作类型");
        }
        String upperAfter = afterTable.toUpperCase(Locale.ROOT);
        if (upperAfter.startsWith("ADD")) {
            String def = afterTable.substring(3).trim();
            if (def.toUpperCase(Locale.ROOT).startsWith("COLUMN")) {
                def = def.substring("COLUMN".length()).trim();
            }
            return new AlterTableParts(tableName, AlterAction.ADD, parseColumnDefinition(def));
        }
        if (upperAfter.startsWith("DROP")) {
            String def = afterTable.substring(4).trim();
            if (def.toUpperCase(Locale.ROOT).startsWith("COLUMN")) {
                def = def.substring("COLUMN".length()).trim();
            }
            String column = domainService.normalizeIdentifier(stripIdentifierQuotes(def.trim()));
            ColumnDefinition placeholder = new ColumnDefinition();
            placeholder.setName(column);
            return new AlterTableParts(tableName, AlterAction.DROP, placeholder);
        }
        if (upperAfter.startsWith("MODIFY") || upperAfter.startsWith("ALTER")) {
            String def = upperAfter.startsWith("MODIFY") ? afterTable.substring(6).trim() : afterTable.substring(5).trim();
            if (def.toUpperCase(Locale.ROOT).startsWith("COLUMN")) {
                def = def.substring("COLUMN".length()).trim();
            }
            return new AlterTableParts(tableName, AlterAction.MODIFY, parseColumnDefinition(def));
        }
        throw new IllegalArgumentException("ALTER TABLE 操作不支持");
    }

    private void applyAlterTable(String databaseName, AlterTableParts parts) {
        List<ColumnDefinition> columns = loadColumns(databaseName, parts.tableName);
        String target = parts.column.getName();
        int idx = findColumnIndex(columns, target);
        if (parts.action == AlterAction.ADD) {
            if (idx != -1) {
                throw new IllegalArgumentException("字段已存在: " + target);
            }
            columns.add(parts.column);
        } else if (parts.action == AlterAction.DROP) {
            if (idx == -1) {
                throw new IllegalArgumentException("字段不存在: " + target);
            }
            columns.remove(idx);
        } else if (parts.action == AlterAction.MODIFY) {
            if (idx == -1) {
                throw new IllegalArgumentException("字段不存在: " + target);
            }
            columns.set(idx, parts.column);
        }
        tableApplicationService.updateTableStructure(databaseName, parts.tableName, columns);
    }

    private List<ColumnDefinition> loadColumns(String databaseName, String tableName) {
        Map<String, Object> detail = tableApplicationService.getTableDetail(databaseName, tableName);
        Object columnsObj = detail.get("columns");
        if (!(columnsObj instanceof List<?> list)) {
            throw new IllegalArgumentException("读取表结构失败");
        }
        List<ColumnDefinition> columns = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                ColumnDefinition column = new ColumnDefinition();
                Object name = map.get("name");
                Object type = map.get("type");
                Object length = map.get("length");
                Object nullable = map.get("nullable");
                Object primaryKey = map.get("primaryKey");
                if (name != null) {
                    column.setName(domainService.normalizeIdentifier(String.valueOf(name)));
                }
                if (type != null) {
                    column.setType(String.valueOf(type));
                }
                if (length instanceof Number num) {
                    column.setLength(num.intValue());
                }
                if (nullable instanceof Boolean bool) {
                    column.setNullable(bool);
                }
                if (primaryKey instanceof Boolean bool) {
                    column.setPk(bool);
                }
                columns.add(column);
            }
        }
        return columns;
    }

    private int findColumnIndex(List<ColumnDefinition> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingParen(String text, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '(') depth++;
            if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("括号不匹配");
    }

    private List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '(') depth++;
                if (c == ')') depth--;
            }
            if (c == separator && depth == 0 && !inString) {
                parts.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private List<String> splitByKeyword(String text, String keyword) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && matchKeywordAt(text, i, keyword)) {
                parts.add(current.toString().trim());
                current.setLength(0);
                i += keyword.length() - 1;
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private boolean matchKeywordAt(String text, int index, String keyword) {
        int end = index + keyword.length();
        if (end > text.length()) {
            return false;
        }
        String slice = text.substring(index, end);
        if (!slice.equalsIgnoreCase(keyword)) {
            return false;
        }
        boolean leftOk = index == 0 || Character.isWhitespace(text.charAt(index - 1));
        boolean rightOk = end == text.length() || Character.isWhitespace(text.charAt(end));
        return leftOk && rightOk;
    }

    private int findKeyword(String textUpper, String keyword) {
        boolean inString = false;
        for (int i = 0; i <= textUpper.length() - keyword.length(); i++) {
            char c = textUpper.charAt(i);
            if (c == '\'' && (i == 0 || textUpper.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && textUpper.startsWith(keyword, i)) {
                return i;
            }
        }
        return -1;
    }

    private int minPositive(int... values) {
        int min = Integer.MAX_VALUE;
        for (int value : values) {
            if (value >= 0 && value < min) {
                min = value;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private record CreateTableParts(String tableName, List<ColumnDefinition> columns) {}

    private record InsertParts(String tableName, Map<String, Object> values) {}

    private record SelectParts(String tableName, List<String> projection, FilterExpression filters, List<OrderBy> orderBy, Limit limit) {}

    private record UpdateParts(String tableName, Map<String, Object> values, FilterExpression filters) {}

    private record DeleteParts(String tableName, FilterExpression filters) {}

    private record OrderBy(String column, boolean asc) {}

    private record Limit(int offset, int size) {}

    private record AlterTableParts(String tableName, AlterAction action, ColumnDefinition column) {}

    private enum AlterAction { ADD, DROP, MODIFY }

    private record FilterPredicate(String column, Operator operator, Object value) {}

    private enum Operator { EQ, NE, GT, LT, GTE, LTE;
        private static Operator fromSymbol(String symbol) {
            return switch (symbol) {
                case "=" -> EQ;
                case "!=" -> NE;
                case ">" -> GT;
                case "<" -> LT;
                case ">=" -> GTE;
                case "<=" -> LTE;
                default -> EQ;
            };
        }
    }

    private record FilterExpression(List<List<FilterPredicate>> groups, boolean hasOr, boolean hasNonEquality) {
        static FilterExpression empty() {
            return new FilterExpression(List.of(), false, false);
        }

        boolean isEmpty() {
            return groups == null || groups.isEmpty();
        }
    }
}