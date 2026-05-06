package com.dbms.backend.application.sql.parser;

import com.dbms.backend.domain.DatabaseDomainService;
import com.dbms.backend.dto.ColumnDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SQL parser that converts normalized SQL into structured commands.
 */
@Component
public class SqlParser {

    private final DatabaseDomainService domainService;

    public SqlParser(DatabaseDomainService domainService) {
        this.domainService = domainService;
    }

    public SqlCommand parse(String normalizedSql) {
        String upper = normalizedSql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("SHOW DATABASES")) {
            return new SqlCommand.ShowDatabases();
        }
        if (upper.startsWith("USE ")) {
            String dbName = extractSingleIdentifier(normalizedSql, "USE");
            return new SqlCommand.UseDatabase(dbName);
        }
        if (upper.startsWith("CREATE DATABASE")) {
            String dbName = extractDatabaseName(normalizedSql, "CREATE DATABASE");
            return new SqlCommand.CreateDatabase(dbName);
        }
        if (upper.startsWith("DROP DATABASE")) {
            String dbName = extractDatabaseName(normalizedSql, "DROP DATABASE");
            return new SqlCommand.DropDatabase(dbName);
        }
        if (upper.startsWith("SHOW TABLES")) {
            return new SqlCommand.ShowTables();
        }
        if (upper.startsWith("DESCRIBE ") || upper.startsWith("DESC ")) {
            String tableName = extractSingleIdentifier(normalizedSql, upper.startsWith("DESCRIBE ") ? "DESCRIBE" : "DESC");
            return new SqlCommand.DescribeTable(tableName);
        }
        if (upper.startsWith("CREATE TABLE")) {
            CreateTableParts parts = parseCreateTable(normalizedSql);
            return new SqlCommand.CreateTable(parts.tableName, parts.columns);
        }
        if (upper.startsWith("DROP TABLE")) {
            String tableName = extractSingleIdentifier(normalizedSql, "DROP TABLE");
            return new SqlCommand.DropTable(tableName);
        }
        if (upper.startsWith("ALTER TABLE")) {
            AlterTableParts parts = parseAlterTable(normalizedSql);
            return new SqlCommand.AlterTable(parts.tableName, parts.action, parts.column);
        }
        if (upper.startsWith("INSERT INTO")) {
            InsertParts parts = parseInsert(normalizedSql);
            return new SqlCommand.Insert(parts.tableName, parts.values);
        }
        if (upper.startsWith("SELECT")) {
            SelectParts parts = parseSelect(normalizedSql);
            return new SqlCommand.Select(parts.tableName, parts.projection, parts.filters, parts.orderBy, parts.limit);
        }
        if (upper.startsWith("UPDATE")) {
            UpdateParts parts = parseUpdate(normalizedSql);
            return new SqlCommand.Update(parts.tableName, parts.values, parts.filters);
        }
        if (upper.startsWith("DELETE FROM")) {
            DeleteParts parts = parseDelete(normalizedSql);
            return new SqlCommand.Delete(parts.tableName, parts.filters);
        }
        throw new IllegalArgumentException("暂不支持的 SQL 语句");
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

        SqlCommand.FilterExpression filters = parseFilterExpression(wherePart);
        List<SqlCommand.OrderBy> orderBy = parseOrderBy(orderPart);
        SqlCommand.Limit limit = parseLimit(limitPart);
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
        SqlCommand.FilterExpression filters = parseFilterExpression(wherePart);
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
        SqlCommand.FilterExpression filters = parseFilterExpression(wherePart);
        return new DeleteParts(tableName, filters);
    }

    private SqlCommand.FilterExpression parseFilterExpression(String wherePart) {
        if (wherePart == null || wherePart.isBlank()) {
            return SqlCommand.FilterExpression.empty();
        }
        List<List<SqlCommand.FilterPredicate>> groups = new ArrayList<>();
        boolean hasOr = false;
        boolean hasNonEquality = false;
        for (String orPart : splitByKeyword(wherePart, "OR")) {
            List<SqlCommand.FilterPredicate> predicates = new ArrayList<>();
            for (String andPart : splitByKeyword(orPart, "AND")) {
                SqlCommand.FilterPredicate predicate = parsePredicate(andPart.trim());
                if (predicate.operator() != SqlCommand.Operator.EQ) {
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
        return new SqlCommand.FilterExpression(groups, hasOr, hasNonEquality);
    }

    private SqlCommand.FilterPredicate parsePredicate(String clause) {
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
                return new SqlCommand.FilterPredicate(column, SqlCommand.Operator.fromSymbol(op), value);
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

    private List<SqlCommand.OrderBy> parseOrderBy(String orderPart) {
        if (orderPart == null || orderPart.isBlank()) {
            return List.of();
        }
        List<SqlCommand.OrderBy> orders = new ArrayList<>();
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
            orders.add(new SqlCommand.OrderBy(column, asc));
        }
        return orders;
    }

    private SqlCommand.Limit parseLimit(String limitPart) {
        if (limitPart == null || limitPart.isBlank()) {
            return null;
        }
        String[] tokens = limitPart.split(",");
        if (tokens.length == 1) {
            int size = Integer.parseInt(tokens[0].trim());
            return new SqlCommand.Limit(0, size);
        }
        int offset = Integer.parseInt(tokens[0].trim());
        int size = Integer.parseInt(tokens[1].trim());
        return new SqlCommand.Limit(offset, size);
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
            return new AlterTableParts(tableName, SqlCommand.AlterAction.ADD, parseColumnDefinition(def));
        }
        if (upperAfter.startsWith("DROP")) {
            String def = afterTable.substring(4).trim();
            if (def.toUpperCase(Locale.ROOT).startsWith("COLUMN")) {
                def = def.substring("COLUMN".length()).trim();
            }
            String column = domainService.normalizeIdentifier(stripIdentifierQuotes(def.trim()));
            ColumnDefinition placeholder = new ColumnDefinition();
            placeholder.setName(column);
            return new AlterTableParts(tableName, SqlCommand.AlterAction.DROP, placeholder);
        }
        if (upperAfter.startsWith("MODIFY") || upperAfter.startsWith("ALTER")) {
            String def = upperAfter.startsWith("MODIFY") ? afterTable.substring(6).trim() : afterTable.substring(5).trim();
            if (def.toUpperCase(Locale.ROOT).startsWith("COLUMN")) {
                def = def.substring("COLUMN".length()).trim();
            }
            return new AlterTableParts(tableName, SqlCommand.AlterAction.MODIFY, parseColumnDefinition(def));
        }
        throw new IllegalArgumentException("ALTER TABLE 操作不支持");
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

    private record SelectParts(String tableName, List<String> projection, SqlCommand.FilterExpression filters,
                               List<SqlCommand.OrderBy> orderBy, SqlCommand.Limit limit) {}

    private record UpdateParts(String tableName, Map<String, Object> values, SqlCommand.FilterExpression filters) {}

    private record DeleteParts(String tableName, SqlCommand.FilterExpression filters) {}

    private record AlterTableParts(String tableName, SqlCommand.AlterAction action, ColumnDefinition column) {}
}
