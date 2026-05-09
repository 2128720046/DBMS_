package com.dbms.backend.modules.sql.executor;

import com.dbms.backend.modules.database.application.DatabaseApplicationService;
import com.dbms.backend.modules.client.application.ClientApplicationService;
import com.dbms.backend.modules.index.application.IndexApplicationService;
import com.dbms.backend.modules.integrity.application.IntegrityApplicationService;
import com.dbms.backend.modules.maintenance.application.MaintenanceApplicationService;
import com.dbms.backend.modules.record.application.RecordApplicationService;
import com.dbms.backend.modules.security.application.SecurityApplicationService;
import com.dbms.backend.modules.table.application.TableApplicationService;
import com.dbms.backend.modules.sql.parser.SqlCommand;
import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.table.dto.ColumnDefinition;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.modules.transaction.application.TransactionApplicationService;
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
    private final IndexApplicationService indexApplicationService;
    private final IntegrityApplicationService integrityApplicationService;
    private final TransactionApplicationService transactionApplicationService;
    private final MaintenanceApplicationService maintenanceApplicationService;
    private final SecurityApplicationService securityApplicationService;
    private final ClientApplicationService clientApplicationService;

    // Tracks the most recent transaction id for COMMIT/ROLLBACK.
    private String currentTransactionId;

    public SqlExecutor(DatabaseDomainService domainService,
                       DatabaseApplicationService databaseApplicationService,
                       TableApplicationService tableApplicationService,
                       RecordApplicationService recordApplicationService,
                       IndexApplicationService indexApplicationService,
                       IntegrityApplicationService integrityApplicationService,
                       TransactionApplicationService transactionApplicationService,
                       MaintenanceApplicationService maintenanceApplicationService,
                       SecurityApplicationService securityApplicationService,
                       ClientApplicationService clientApplicationService) {
        this.domainService = domainService;
        this.databaseApplicationService = databaseApplicationService;
        this.tableApplicationService = tableApplicationService;
        this.recordApplicationService = recordApplicationService;
        this.indexApplicationService = indexApplicationService;
        this.integrityApplicationService = integrityApplicationService;
        this.transactionApplicationService = transactionApplicationService;
        this.maintenanceApplicationService = maintenanceApplicationService;
        this.securityApplicationService = securityApplicationService;
        this.clientApplicationService = clientApplicationService;
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
        if (command instanceof SqlCommand.CreateUser createUser) {
            securityApplicationService.register(createUser.username(), createUser.password());
            return buildMessagePayload("用户创建成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.DropUser dropUser) {
            securityApplicationService.dropUser(dropUser.username());
            return buildMessagePayload("用户删除成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.AlterUser alterUser) {
            securityApplicationService.alterUser(alterUser.username(), alterUser.password());
            return buildMessagePayload("用户修改成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.GrantPrivilege grant) {
            securityApplicationService.grant(grant.username(), grant.privilege(), grant.objectName());
            return buildMessagePayload("权限授予成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.RevokePrivilege revoke) {
            securityApplicationService.revoke(revoke.username(), revoke.privilege(), revoke.objectName());
            return buildMessagePayload("权限撤销成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.Connect connect) {
            Map<String, Object> user = securityApplicationService.login(connect.username(), connect.password());
            String clientId = clientApplicationService.connect(connect.username());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "message");
            payload.put("status", "success");
            payload.put("data", "连接成功");
            payload.put("clientId", clientId);
            payload.put("username", user.get("username"));
            payload.put("token", user.get("token"));
            payload.put("affectedRows", 1);
            payload.put("refreshTree", false);
            return payload;
        }
        if (command instanceof SqlCommand.Disconnect disconnect) {
            String clientId = disconnect.clientId();
            if (clientId != null && !clientId.isEmpty()) {
                clientApplicationService.disconnect(clientId);
            }
            return buildMessagePayload("连接已断开", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.ShowClients) {
            List<Map<String, Object>> rows = clientApplicationService.listOnlineClientDetails();
            return buildTablePayloadFromRows(rows, List.of(
                    "CLIENT_ID", "USER", "IP_ADDRESS", "PORT", "CONNECTED_AT", "CURRENT_DATABASE"
            ));
        }
        if (command instanceof SqlCommand.ShowGrants showGrants) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("USER", showGrants.username());
            row.put("GRANTS", securityApplicationService.permissionsOf(showGrants.username(), "*", "*"));
            return buildTablePayloadFromRows(List.of(row), List.of("USER", "GRANTS"));
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
        if (command instanceof SqlCommand.ReplaceColumns replaceColumns) {
            tableApplicationService.updateTableStructure(normalizedDb, replaceColumns.tableName(), replaceColumns.columns());
            return buildMessagePayload("表结构更新成功", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.Insert insert) {
            Map<String, Object> values = new LinkedHashMap<>();
            List<String> columns = insert.columns();
            List<Object> rawValues = insert.values();
            if (columns == null || columns.isEmpty()) {
                List<String> ordered = readColumnOrder(normalizedDb, insert.tableName());
                if (ordered.isEmpty()) {
                    throw new IllegalArgumentException("无法获取表字段顺序，INSERT 失败");
                }
                if (rawValues.size() != ordered.size()) {
                    throw new IllegalArgumentException("INSERT INTO VALUES 值数量与表字段数不匹配");
                }
                for (int i = 0; i < ordered.size(); i++) {
                    values.put(ordered.get(i), rawValues.get(i));
                }
            } else {
                if (rawValues.size() != columns.size()) {
                    throw new IllegalArgumentException("INSERT INTO 字段和值数量不匹配");
                }
                for (int i = 0; i < columns.size(); i++) {
                    values.put(columns.get(i), rawValues.get(i));
                }
            }

            int affected = recordApplicationService.insert(normalizedDb, insert.tableName(), values);

            // 事务内注册撤销操作：删除刚插入的行
            if (currentTransactionId != null) {
                Map<String, Object> insertedValues = new LinkedHashMap<>(values);
                String dbName = normalizedDb;
                String tblName = insert.tableName();
                transactionApplicationService.recordUndoOperation(currentTransactionId, () -> {
                    try {
                        recordApplicationService.delete(dbName, tblName, insertedValues);
                    } catch (Exception ignored) {
                        // 撤销失败不阻断
                    }
                });
            }

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

            // 事务内：先查询旧值用于可能的回滚
            List<Map<String, Object>> oldRows = null;
            if (currentTransactionId != null) {
                oldRows = recordApplicationService.query(normalizedDb, update.tableName(),
                        toEqualityMap(update.filters()), 200, 0);
            }

            int affected = recordApplicationService.update(normalizedDb, update.tableName(),
                    toEqualityMap(update.filters()), update.values());

            // 事务内注册撤销操作：用旧值覆盖回原来的数据
            if (currentTransactionId != null && oldRows != null && !oldRows.isEmpty()) {
                String dbName = normalizedDb;
                String tblName = update.tableName();
                Map<String, Object> updateValues = new LinkedHashMap<>(update.values());
                List<Map<String, Object>> savedOldRows = new ArrayList<>(oldRows);
                transactionApplicationService.recordUndoOperation(currentTransactionId, () -> {
                    for (Map<String, Object> row : savedOldRows) {
                        try {
                            Map<String, Object> restore = new LinkedHashMap<>();
                            for (String col : updateValues.keySet()) {
                                restore.put(col, row.get(col));
                            }
                            // 以原始过滤条件定位行，恢复旧值
                            recordApplicationService.update(dbName, tblName,
                                    toEqualityMap(update.filters()), restore);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            return buildMessagePayload("更新成功", affected, normalizedSql);
        }
        if (command instanceof SqlCommand.Delete delete) {
            assertSimpleFilter(delete.filters(), "DELETE");

            // 事务内：先查询被删除的行用于可能的回滚
            List<Map<String, Object>> deletedRows = null;
            if (currentTransactionId != null) {
                deletedRows = recordApplicationService.query(normalizedDb, delete.tableName(),
                        toEqualityMap(delete.filters()), 200, 0);
            }

            int affected = recordApplicationService.delete(normalizedDb, delete.tableName(),
                    toEqualityMap(delete.filters()));

            // 事务内注册撤销操作：重新插入被删除的行
            if (currentTransactionId != null && deletedRows != null && !deletedRows.isEmpty()) {
                String dbName = normalizedDb;
                String tblName = delete.tableName();
                List<Map<String, Object>> savedRows = new ArrayList<>(deletedRows);
                transactionApplicationService.recordUndoOperation(currentTransactionId, () -> {
                    for (Map<String, Object> row : savedRows) {
                        try {
                            recordApplicationService.insert(dbName, tblName, row);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            return buildMessagePayload("删除成功", affected, normalizedSql);
        }
        if (command instanceof SqlCommand.ShowIndexes showIndexes) {
            return buildTablePayloadFromRows(indexApplicationService.listIndexes(normalizedDb, showIndexes.tableName()),
                    List.of("name", "columns", "unique", "ascending"));
        }
        if (command instanceof SqlCommand.CreateIndex createIndex) {
            indexApplicationService.createIndex(normalizedDb, createIndex.tableName(), createIndex.indexName(),
                    createIndex.columns(), createIndex.unique(), createIndex.ascending());
            return buildMessagePayload("索引创建成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.DropIndex dropIndex) {
            indexApplicationService.dropIndex(normalizedDb, dropIndex.tableName(), dropIndex.indexName());
            return buildMessagePayload("索引删除成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.RebuildIndex rebuildIndex) {
            indexApplicationService.rebuildIndex(normalizedDb, rebuildIndex.tableName(), rebuildIndex.indexName());
            return buildMessagePayload("索引重建成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.ShowConstraints showConstraints) {
            return buildTablePayloadFromRows(integrityApplicationService.listConstraints(normalizedDb, showConstraints.tableName()),
                    List.of("name", "type", "column", "parameter"));
        }
        if (command instanceof SqlCommand.AddConstraint addConstraint) {
            integrityApplicationService.addConstraint(normalizedDb, addConstraint.tableName(), addConstraint.constraintName(),
                    "", "RAW", addConstraint.definition());
            return buildMessagePayload("约束创建成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.DropConstraint dropConstraint) {
            integrityApplicationService.dropConstraint(normalizedDb, dropConstraint.tableName(), dropConstraint.constraintName());
            return buildMessagePayload("约束删除成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.CheckConstraints checkConstraints) {
            List<Map<String, Object>> issues = integrityApplicationService.validateTable(normalizedDb, checkConstraints.tableName());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "message");
            payload.put("status", "success");
            payload.put("data", issues.isEmpty() ? "完整性校验通过" : "发现完整性问题");
            payload.put("passed", issues.isEmpty());
            payload.put("issues", issues);
            payload.put("affectedRows", issues.size());
            return payload;
        }

        if (command instanceof SqlCommand.BeginTransaction) {
            currentTransactionId = transactionApplicationService.begin(normalizedDb);
            return buildMessagePayload("事务已开启: " + currentTransactionId, 0, normalizedSql);
        }
        if (command instanceof SqlCommand.CommitTransaction) {
            transactionApplicationService.commit(currentTransactionId != null ? currentTransactionId : normalizedDb);
            currentTransactionId = null;
            return buildMessagePayload("事务已提交", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.RollbackTransaction) {
            transactionApplicationService.rollback(currentTransactionId != null ? currentTransactionId : normalizedDb);
            currentTransactionId = null;
            return buildMessagePayload("事务已回滚", 0, normalizedSql);
        }
        if (command instanceof SqlCommand.ShowBackups showBackups) {
            return buildTablePayloadFromRows(maintenanceApplicationService.listBackups(showBackups.databaseName()),
                    List.of("name", "size", "updatedAt", "desc"));
        }
        if (command instanceof SqlCommand.BackupDatabase backupDatabase) {
            maintenanceApplicationService.backup(backupDatabase.databaseName(), java.nio.file.Path.of(backupDatabase.targetPath()));
            return buildMessagePayload("备份创建成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.RestoreDatabase restoreDatabase) {
            maintenanceApplicationService.restore(restoreDatabase.databaseName(), java.nio.file.Path.of(restoreDatabase.backupPath()));
            return buildMessagePayload("数据库还原成功", 1, normalizedSql);
        }
        if (command instanceof SqlCommand.DeleteBackup deleteBackup) {
            maintenanceApplicationService.deleteBackup(deleteBackup.databaseName(), deleteBackup.backupName());
            return buildMessagePayload("备份删除成功", 1, normalizedSql);
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



