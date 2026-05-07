package com.dbms.backend.application.sql.parser;

import com.dbms.backend.dto.ColumnDefinition;

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

    record Insert(String tableName, Map<String, Object> values) implements SqlCommand {}

    record Select(String tableName, List<String> projection, FilterExpression filters, List<OrderBy> orderBy, Limit limit)
            implements SqlCommand {}

    record Update(String tableName, Map<String, Object> values, FilterExpression filters) implements SqlCommand {}

    record Delete(String tableName, FilterExpression filters) implements SqlCommand {}

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
