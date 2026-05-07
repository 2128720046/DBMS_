package com.dbms.backend.application.sql.executor;

import com.dbms.backend.application.DatabaseApplicationService;
import com.dbms.backend.application.RecordApplicationService;
import com.dbms.backend.application.TableApplicationService;
import com.dbms.backend.application.sql.parser.SqlCommand;
import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.dto.ColumnDefinition;
import com.dbms.backend.infrastructure.storage.config.StorageEngineConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行器：根据结构化命令路由到应用服务并组装返回结构。
 */
@Component
public class SqlExecutor {

    private final DatabaseDomainService domainService;
    private final DatabaseApplicationService databaseApplicationService;
    private final TableApplicationService tableApplicationService;
    private final RecordApplicationService recordApplicationService;

    public SqlExecutor(DatabaseDomainService domainService,
                       DatabaseApplicationService databaseApplicationService,
                       TableApplicationService tableApplicationService,
                       RecordApplicationService recordApplicationService) {
        this.domainService = domainService;
        this.databaseApplicationService = databaseApplicationService;
        this.tableApplicationService = tableApplicationService;
        this.recordApplicationService = recordApplicationService;
    }

    public Map<String, Object> execute(String databaseName, String normalizedSql, SqlCommand command) {
        if (command instanceof SqlCommand.ShowDatabases) {
            return buildDatabaseListPayload();
        }
        if (command instanceof SqlCommand.UseDatabase) {
            return buildMessagePayload("已切换数据库", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.CreateDatabase createDatabase) {
            databaseApplicationService.createDatabase(createDatabase.databaseName());
            return buildMessagePayload("数据库创建成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.DropDatabase dropDatabase) {
            databaseApplicationService.dropDatabase(dropDatabase.databaseName());
            return buildMessagePayload("数据库删除成功", 1, normalizedSql);
        }

        String normalizedDb = requireDatabase(databaseName);

        if (command instanceof SqlCommand.ShowTables) {
            return buildTableListPayload(normalizedDb);
        }
        if (command instanceof SqlCommand.DescribeTable describeTable) {
            return buildDescribePayload(normalizedDb, describeTable.tableName());
        }
        if (command instanceof SqlCommand.CreateTable createTable) {
            String targetDb = databaseName != null && !databaseName.isEmpty()
                    ? normalizedDb
                    : StorageEngineConfig.getSystemSchemaName();
            tableApplicationService.createTable(targetDb, createTable.tableName(), createTable.columns());
            return buildMessagePayload("表创建成功", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.DropTable dropTable) {
            tableApplicationService.dropTable(normalizedDb, dropTable.tableName());
            return buildMessagePayload("表删除成功", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.AlterTable alterTable) {
            applyAlterTable(normalizedDb, alterTable);
            return buildMessagePayload("表结构更新成功", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.Insert insert) {
            int affected = recordApplicationService.insert(normalizedDb, insert.tableName(), insert.values());
            return buildMessagePayload("插入成功", affected, normalizedSql);
        }
        if (command instanceof SqlCommand.Select select) {
            List<Map<String, Object>> rows = fetchAllRows(normalizedDb, select.tableName());
            rows = filterRows(rows, select.filters());
            rows = sortRows(rows, select.orderBy());
            rows = applyLimit(rows, select.limit());
            List<String> columnOrder = select.projection();
            if (columnOrder == null || columnOrder.isEmpty()) {
                columnOrder = readColumnOrder(normalizedDb, select.tableName());
            }
            return buildTablePayloadFromRows(rows, columnOrder);
        }
        if (command instanceof SqlCommand.Update update) {
            assertSimpleFilter(update.filters(), "UPDATE");
            int affected = recordApplicationService.update(normalizedDb, update.tableName(),
                    toEqualityMap(update.filters()), update.values());
            return buildMessagePayload("更新成功", affected, normalizedSql);
        }
        if (command instanceof SqlCommand.Delete delete) {
            assertSimpleFilter(delete.filters(), "DELETE");
            int affected = recordApplicationService.delete(normalizedDb, delete.tableName(),
                    toEqualityMap(delete.filters()));
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

    private List<Map<String, Object>> filterRows(List<Map<String, Object>> rows, SqlCommand.FilterExpression expression) {
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

    private boolean matchesExpression(Map<String, Object> row, SqlCommand.FilterExpression expression) {
        for (List<SqlCommand.FilterPredicate> group : expression.groups()) {
            boolean groupMatch = true;
            for (SqlCommand.FilterPredicate predicate : group) {
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

    private boolean matchesPredicate(Map<String, Object> row, SqlCommand.FilterPredicate predicate) {
        Object value = row.get(predicate.column());
        Object literal = predicate.value();
        if (predicate.operator() == SqlCommand.Operator.EQ) {
            return compareValues(value, literal) == 0;
        }
        if (predicate.operator() == SqlCommand.Operator.NE) {
            return compareValues(value, literal) != 0;
        }
        int cmp = compareValues(value, literal);
        return switch (predicate.operator()) {
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

    private List<Map<String, Object>> sortRows(List<Map<String, Object>> rows, List<SqlCommand.OrderBy> orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        Comparator<Map<String, Object>> comparator = null;
        for (SqlCommand.OrderBy order : orderBy) {
            Comparator<Map<String, Object>> next = Comparator.comparing(row -> row.get(order.column()), this::compareValuesNullSafe);
            if (!order.asc()) {
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

    private List<Map<String, Object>> applyLimit(List<Map<String, Object>> rows, SqlCommand.Limit limit) {
        if (limit == null) {
            return rows;
        }
        int offset = Math.max(limit.offset(), 0);
        int end = limit.size() < 0 ? rows.size() : Math.min(rows.size(), offset + limit.size());
        if (offset >= rows.size()) {
            return List.of();
        }
        return new ArrayList<>(rows.subList(offset, end));
    }

    private void assertSimpleFilter(SqlCommand.FilterExpression expression, String action) {
        if (expression.hasOr() || expression.hasNonEquality()) {
            throw new IllegalArgumentException(action + " 仅支持 AND 等值条件");
        }
    }

    private Map<String, Object> toEqualityMap(SqlCommand.FilterExpression expression) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (expression.isEmpty()) {
            return filters;
        }
        for (SqlCommand.FilterPredicate predicate : expression.groups().get(0)) {
            filters.put(predicate.column(), predicate.value());
        }
        return filters;
    }

    private void applyAlterTable(String databaseName, SqlCommand.AlterTable parts) {
        List<ColumnDefinition> columns = loadColumns(databaseName, parts.tableName());
        String target = parts.column().getName();
        int idx = findColumnIndex(columns, target);
        if (parts.action() == SqlCommand.AlterAction.ADD) {
            if (idx != -1) {
                throw new IllegalArgumentException("字段已存在: " + target);
            }
            columns.add(parts.column());
        } else if (parts.action() == SqlCommand.AlterAction.DROP) {
            if (idx == -1) {
                throw new IllegalArgumentException("字段不存在: " + target);
            }
            columns.remove(idx);
        } else if (parts.action() == SqlCommand.AlterAction.MODIFY) {
            if (idx == -1) {
                throw new IllegalArgumentException("字段不存在: " + target);
            }
            columns.set(idx, parts.column());
        }
        tableApplicationService.updateTableStructure(databaseName, parts.tableName(), columns);
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
}
