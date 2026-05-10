package com.dbms.backend.modules.sql.parser;

import com.dbms.backend.modules.table.dto.ColumnDefinition;

import java.util.List;
import java.util.Map;

/**
 * SQL 结构化命令模型，承载解析后的语义数据。
 */
public interface SqlCommand {

    record ShowDatabases() implements SqlCommand {}

    record UseDatabase(String databaseName) implements SqlCommand {}

    record CreateDatabase(String databaseName) implements SqlCommand {}

    record DropDatabase(String databaseName) implements SqlCommand {}

    record ShowTables() implements SqlCommand {}

    record DescribeTable(String tableName) implements SqlCommand {}

    record CreateTable(String tableName, List<ColumnDefinition> columns) implements SqlCommand {}

    record DropTable(String tableName) implements SqlCommand {}

    record AlterTable(String tableName, AlterAction action, ColumnDefinition column) implements SqlCommand {}

    record ReplaceColumns(String tableName, List<ColumnDefinition> columns) implements SqlCommand {}

    /**
     * INSERT 命令。
     * <p>
     * columns 为空表示未显式指定字段列表（即 INSERT INTO t VALUES (...)），
     * 此时执行器需要按表结构字段顺序进行映射。
     * </p>
     */
    record Insert(String tableName, List<String> columns, List<Object> values) implements SqlCommand {}

    record Select(String tableName, List<String> projection, FilterExpression filters, List<OrderBy> orderBy,
                  Limit limit, List<JoinSpec> joins)
            implements SqlCommand {}

    /**
     * JOIN 语义描述（仅支持单次等值内连接）。
     */
    record JoinSpec(JoinType type, String tableName, String leftTable, String leftColumn, String rightTable, String rightColumn) {}

    enum JoinType {
        INNER,
        LEFT,
        RIGHT
    }

    record Update(String tableName, Map<String, Object> values, FilterExpression filters) implements SqlCommand {}

    record Delete(String tableName, FilterExpression filters) implements SqlCommand {}

    record CreateIndex(String indexName, String tableName, List<String> columns, boolean unique, boolean ascending)
            implements SqlCommand {}

    record DropIndex(String indexName, String tableName) implements SqlCommand {}

    record ShowIndexes(String tableName) implements SqlCommand {}

    record RebuildIndex(String indexName, String tableName) implements SqlCommand {}

    record ShowConstraints(String tableName) implements SqlCommand {}

    record AddConstraint(String tableName, String constraintName, String definition) implements SqlCommand {}

    record DropConstraint(String tableName, String constraintName) implements SqlCommand {}

    record CheckConstraints(String tableName) implements SqlCommand {}

    record BeginTransaction() implements SqlCommand {}

    record CommitTransaction() implements SqlCommand {}

    record RollbackTransaction() implements SqlCommand {}

    record BackupDatabase(String databaseName, String targetPath) implements SqlCommand {}

    record RestoreDatabase(String databaseName, String backupPath) implements SqlCommand {}

    record ShowBackups(String databaseName) implements SqlCommand {}

    record DeleteBackup(String databaseName, String backupName) implements SqlCommand {}

    record CreateUser(String username, String password) implements SqlCommand {}

    record DropUser(String username) implements SqlCommand {}

    record AlterUser(String username, String password) implements SqlCommand {}

    record GrantPrivilege(String privilege, String objectName, String username) implements SqlCommand {}

    record RevokePrivilege(String privilege, String objectName, String username) implements SqlCommand {}

    record ShowGrants(String username) implements SqlCommand {}

    record Connect(String serverAddress, Integer port, String username, String password) implements SqlCommand {}

    record Disconnect(String clientId) implements SqlCommand {}

    record ShowClients() implements SqlCommand {}

    enum AlterAction {
        ADD,
        DROP,
        MODIFY
    }

    record OrderBy(String column, boolean asc) {}

    record Limit(int offset, int size) {}

    record FilterPredicate(String column, Operator operator, Object value) {}

    enum Operator {
        EQ,
        NE,
        GT,
        LT,
        GTE,
        LTE;

        static Operator fromSymbol(String symbol) {
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

    record FilterExpression(List<List<FilterPredicate>> groups, boolean hasOr, boolean hasNonEquality) {
        public static FilterExpression empty() {
            return new FilterExpression(List.of(), false, false);
        }

        public boolean isEmpty() {
            return groups == null || groups.isEmpty();
        }
    }
}



