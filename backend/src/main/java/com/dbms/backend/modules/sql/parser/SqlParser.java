package com.dbms.backend.modules.sql.parser;

import com.dbms.backend.core.naming.DatabaseDomainService;
import com.dbms.backend.modules.table.dto.ColumnDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SQL 解析器：把规范化后的 SQL 字符串解析成结构化命令。
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
        if (upper.startsWith("ALTER TABLE") && upper.contains(" REPLACE COLUMNS ")) {
            ReplaceColumnsParts parts = parseReplaceColumns(normalizedSql);
            return new SqlCommand.ReplaceColumns(parts.tableName, parts.columns);
        }
        if (upper.startsWith("ALTER TABLE")
                && !upper.contains(" ADD CONSTRAINT ")
                && !upper.contains(" DROP CONSTRAINT ")) {
            AlterTableParts parts = parseAlterTable(normalizedSql);
            return new SqlCommand.AlterTable(parts.tableName, parts.action, parts.column);
        }
        if (upper.startsWith("INSERT INTO")) {
            InsertParts parts = parseInsert(normalizedSql);
            return new SqlCommand.Insert(parts.tableName, parts.columns, parts.values);
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
        if (upper.startsWith("CREATE UNIQUE INDEX") || upper.startsWith("CREATE INDEX")) {
            return parseCreateIndex(normalizedSql);
        }
        if (upper.startsWith("DROP INDEX")) {
            String rest = normalizedSql.substring("DROP INDEX".length()).trim();
            String indexName = domainService.normalizeIdentifier(extractIdentifierToken(rest));
            int onIndex = rest.toUpperCase(Locale.ROOT).indexOf(" ON ");
            String tableName = onIndex == -1 ? "" : domainService.normalizeIdentifier(extractIdentifierToken(rest.substring(onIndex + 4)));
            return new SqlCommand.DropIndex(indexName, tableName);
        }
        if (upper.startsWith("SHOW INDEXES FROM") || upper.startsWith("SHOW INDEX FROM")) {
            String keyword = upper.startsWith("SHOW INDEXES FROM") ? "SHOW INDEXES FROM" : "SHOW INDEX FROM";
            return new SqlCommand.ShowIndexes(extractSingleIdentifier(normalizedSql, keyword));
        }
        if (upper.startsWith("REBUILD INDEX")) {
            String rest = normalizedSql.substring("REBUILD INDEX".length()).trim();
            String indexName = domainService.normalizeIdentifier(extractIdentifierToken(rest));
            int onIndex = rest.toUpperCase(Locale.ROOT).indexOf(" ON ");
            String tableName = onIndex == -1 ? "" : domainService.normalizeIdentifier(extractIdentifierToken(rest.substring(onIndex + 4)));
            return new SqlCommand.RebuildIndex(indexName, tableName);
        }
        if (upper.startsWith("SHOW CONSTRAINTS FROM")) {
            return new SqlCommand.ShowConstraints(extractSingleIdentifier(normalizedSql, "SHOW CONSTRAINTS FROM"));
        }
        if (upper.startsWith("CHECK CONSTRAINTS FROM")) {
            return new SqlCommand.CheckConstraints(extractSingleIdentifier(normalizedSql, "CHECK CONSTRAINTS FROM"));
        }
        if (upper.startsWith("ALTER TABLE") && upper.contains(" ADD CONSTRAINT ")) {
            return parseAddConstraint(normalizedSql);
        }
        if (upper.startsWith("ALTER TABLE") && upper.contains(" DROP CONSTRAINT ")) {
            return parseDropConstraint(normalizedSql);
        }
        if (upper.equals("BEGIN") || upper.equals("START TRANSACTION")) {
            return new SqlCommand.BeginTransaction();
        }
        if (upper.equals("COMMIT")) {
            return new SqlCommand.CommitTransaction();
        }
        if (upper.equals("ROLLBACK")) {
            return new SqlCommand.RollbackTransaction();
        }
        if (upper.startsWith("BACKUP DATABASE")) {
            return parseBackupDatabase(normalizedSql);
        }
        if (upper.startsWith("RESTORE DATABASE")) {
            return parseRestoreDatabase(normalizedSql);
        }
        if (upper.startsWith("SHOW BACKUPS FROM")) {
            return new SqlCommand.ShowBackups(extractDatabaseName(normalizedSql, "SHOW BACKUPS FROM"));
        }
        if (upper.startsWith("DELETE BACKUP")) {
            return parseDeleteBackup(normalizedSql);
        }
        if (upper.startsWith("CREATE USER")) {
            return parseCreateUser(normalizedSql);
        }
        if (upper.startsWith("DROP USER")) {
            return new SqlCommand.DropUser(extractQuotedOrIdentifier(normalizedSql.substring("DROP USER".length()).trim()));
        }
        if (upper.startsWith("ALTER USER")) {
            return parseAlterUser(normalizedSql);
        }
        if (upper.startsWith("GRANT")) {
            return parseGrant(normalizedSql);
        }
        if (upper.startsWith("REVOKE")) {
            return parseRevoke(normalizedSql);
        }
        if (upper.startsWith("SHOW GRANTS FOR")) {
            return new SqlCommand.ShowGrants(extractQuotedOrIdentifier(normalizedSql.substring("SHOW GRANTS FOR".length()).trim()));
        }
        if (upper.startsWith("CONNECT TO")) {
            return parseConnect(normalizedSql);
        }
        if (upper.startsWith("DISCONNECT")) {
            String rest = normalizedSql.substring("DISCONNECT".length()).trim();
            String clientId = rest.isEmpty() ? "" : rest;
            return new SqlCommand.Disconnect(clientId);
        }
        if (upper.equals("SHOW CLIENTS")) {
            return new SqlCommand.ShowClients();
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

        String rawTableToken = extractIdentifierToken(rest);
        String tableName = domainService.normalizeIdentifier(rawTableToken);
        int tableTokenIndex = rest.toUpperCase(Locale.ROOT).indexOf(rawTableToken.toUpperCase(Locale.ROOT));
        if (tableTokenIndex == -1) {
            throw new IllegalArgumentException("INSERT INTO 表名解析失败");
        }
        String afterTable = rest.substring(tableTokenIndex + rawTableToken.length()).trim();

        int valuesIndex = upper.indexOf("VALUES", intoIndex);
        if (valuesIndex == -1) {
            throw new IllegalArgumentException("INSERT INTO 缺少 VALUES");
        }

        List<String> columns = null;
        if (!afterTable.isEmpty() && afterTable.charAt(0) == '(') {
            int columnsStart = rest.indexOf('(');
            int columnsEnd = findMatchingParen(rest, columnsStart);
            String columnsPart = rest.substring(columnsStart + 1, columnsEnd).trim();
            columns = new ArrayList<>();
            for (String item : splitTopLevel(columnsPart, ',')) {
                columns.add(domainService.normalizeIdentifier(stripIdentifierQuotes(item.trim())));
            }
        }

        String valuesRest = sql.substring(valuesIndex + "VALUES".length()).trim();
        int valuesStart = valuesRest.indexOf('(');
        int valuesEnd = findMatchingParen(valuesRest, valuesStart);
        String valuesPart = valuesRest.substring(valuesStart + 1, valuesEnd).trim();

        List<Object> parsedValues = new ArrayList<>();
        for (String item : splitTopLevel(valuesPart, ',')) {
            parsedValues.add(parseLiteral(item.trim()));
        }

        if (columns != null && columns.size() != parsedValues.size()) {
            throw new IllegalArgumentException("INSERT INTO 字段和值数量不匹配");
        }
        return new InsertParts(tableName, columns, parsedValues);
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

    private SqlCommand.CreateIndex parseCreateIndex(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        boolean unique = upper.startsWith("CREATE UNIQUE INDEX");
        String keyword = unique ? "CREATE UNIQUE INDEX" : "CREATE INDEX";
        String rest = sql.substring(keyword.length()).trim();
        String indexName = domainService.normalizeIdentifier(extractIdentifierToken(rest));
        int onIndex = rest.toUpperCase(Locale.ROOT).indexOf(" ON ");
        if (onIndex == -1) {
            throw new IllegalArgumentException("CREATE INDEX 缺少 ON table_name");
        }
        String afterOn = rest.substring(onIndex + 4).trim();
        String tableName = domainService.normalizeIdentifier(extractIdentifierToken(afterOn));
        int parenStart = afterOn.indexOf('(');
        int parenEnd = findMatchingParen(afterOn, parenStart);
        List<String> columns = new ArrayList<>();
        boolean asc = true;
        for (String item : splitTopLevel(afterOn.substring(parenStart + 1, parenEnd), ',')) {
            String[] tokens = item.trim().split("\\s+");
            columns.add(domainService.normalizeIdentifier(stripIdentifierQuotes(tokens[0])));
            if (tokens.length > 1 && tokens[1].equalsIgnoreCase("DESC")) {
                asc = false;
            }
        }
        return new SqlCommand.CreateIndex(indexName, tableName, columns, unique, asc);
    }

    private ReplaceColumnsParts parseReplaceColumns(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        String rest = sql.substring("ALTER TABLE".length()).trim();
        String tableName = domainService.normalizeIdentifier(extractIdentifierToken(rest));
        int idx = upper.indexOf(" REPLACE COLUMNS ");
        String after = sql.substring(idx + " REPLACE COLUMNS ".length()).trim();
        int parenStart = after.indexOf('(');
        int parenEnd = findMatchingParen(after, parenStart);
        List<ColumnDefinition> columns = new ArrayList<>();
        for (String def : splitTopLevel(after.substring(parenStart + 1, parenEnd), ',')) {
            columns.add(parseColumnDefinition(def.trim()));
        }
        return new ReplaceColumnsParts(tableName, columns);
    }

    private SqlCommand.AddConstraint parseAddConstraint(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        String rest = sql.substring("ALTER TABLE".length()).trim();
        String tableName = domainService.normalizeIdentifier(extractIdentifierToken(rest));
        int idx = upper.indexOf(" ADD CONSTRAINT ");
        String after = sql.substring(idx + " ADD CONSTRAINT ".length()).trim();
        String constraintName = domainService.normalizeIdentifier(extractIdentifierToken(after));
        String definition = after.substring(extractIdentifierToken(after).length()).trim();
        return new SqlCommand.AddConstraint(tableName, constraintName, definition);
    }

    private SqlCommand.DropConstraint parseDropConstraint(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        String rest = sql.substring("ALTER TABLE".length()).trim();
        String tableName = domainService.normalizeIdentifier(extractIdentifierToken(rest));
        int idx = upper.indexOf(" DROP CONSTRAINT ");
        String constraintName = domainService.normalizeIdentifier(extractIdentifierToken(sql.substring(idx + " DROP CONSTRAINT ".length()).trim()));
        return new SqlCommand.DropConstraint(tableName, constraintName);
    }

    private SqlCommand.BackupDatabase parseBackupDatabase(String sql) {
        String rest = sql.substring("BACKUP DATABASE".length()).trim();
        String dbName = domainService.normalizeDatabaseName(extractIdentifierToken(rest));
        int toIndex = rest.toUpperCase(Locale.ROOT).indexOf(" TO ");
        String target = toIndex == -1 ? "" : extractQuotedOrIdentifier(rest.substring(toIndex + 4).trim());
        return new SqlCommand.BackupDatabase(dbName, target);
    }

    private SqlCommand.RestoreDatabase parseRestoreDatabase(String sql) {
        String rest = sql.substring("RESTORE DATABASE".length()).trim();
        String dbName = domainService.normalizeDatabaseName(extractIdentifierToken(rest));
        int fromIndex = rest.toUpperCase(Locale.ROOT).indexOf(" FROM ");
        String source = fromIndex == -1 ? "" : extractQuotedOrIdentifier(rest.substring(fromIndex + 6).trim());
        return new SqlCommand.RestoreDatabase(dbName, source);
    }

    private SqlCommand.DeleteBackup parseDeleteBackup(String sql) {
        String rest = sql.substring("DELETE BACKUP".length()).trim();
        String backupName = extractQuotedOrIdentifier(rest);
        int fromIndex = rest.toUpperCase(Locale.ROOT).indexOf(" FROM ");
        String dbName = fromIndex == -1 ? "" : domainService.normalizeDatabaseName(extractIdentifierToken(rest.substring(fromIndex + 6)));
        return new SqlCommand.DeleteBackup(dbName, backupName);
    }

    private SqlCommand.CreateUser parseCreateUser(String sql) {
        String rest = sql.substring("CREATE USER".length()).trim();
        String username = extractQuotedOrIdentifier(rest);
        int idx = rest.toUpperCase(Locale.ROOT).indexOf(" IDENTIFIED BY ");
        String password = idx == -1 ? "" : extractQuotedOrIdentifier(rest.substring(idx + " IDENTIFIED BY ".length()).trim());
        return new SqlCommand.CreateUser(username, password);
    }

    private SqlCommand.AlterUser parseAlterUser(String sql) {
        String rest = sql.substring("ALTER USER".length()).trim();
        String username = extractQuotedOrIdentifier(rest);
        int idx = rest.toUpperCase(Locale.ROOT).indexOf(" IDENTIFIED BY ");
        String password = idx == -1 ? "" : extractQuotedOrIdentifier(rest.substring(idx + " IDENTIFIED BY ".length()).trim());
        return new SqlCommand.AlterUser(username, password);
    }

    private SqlCommand.GrantPrivilege parseGrant(String sql) {
        String rest = sql.substring("GRANT".length()).trim();
        int onIndex = rest.toUpperCase(Locale.ROOT).indexOf(" ON ");
        int toIndex = rest.toUpperCase(Locale.ROOT).indexOf(" TO ");
        String privilege = onIndex == -1 ? rest : rest.substring(0, onIndex).trim();
        String objectName = onIndex == -1 || toIndex == -1 ? "" : rest.substring(onIndex + 4, toIndex).trim();
        String username = toIndex == -1 ? "" : extractQuotedOrIdentifier(rest.substring(toIndex + 4).trim());
        return new SqlCommand.GrantPrivilege(privilege, objectName, username);
    }

    private SqlCommand.RevokePrivilege parseRevoke(String sql) {
        String rest = sql.substring("REVOKE".length()).trim();
        int onIndex = rest.toUpperCase(Locale.ROOT).indexOf(" ON ");
        int fromIndex = rest.toUpperCase(Locale.ROOT).indexOf(" FROM ");
        String privilege = onIndex == -1 ? rest : rest.substring(0, onIndex).trim();
        String objectName = onIndex == -1 || fromIndex == -1 ? "" : rest.substring(onIndex + 4, fromIndex).trim();
        String username = fromIndex == -1 ? "" : extractQuotedOrIdentifier(rest.substring(fromIndex + 6).trim());
        return new SqlCommand.RevokePrivilege(privilege, objectName, username);
    }

    private SqlCommand.Connect parseConnect(String sql) {
        String rest = sql.substring("CONNECT TO".length()).trim();
        String server = extractIdentifierToken(rest);
        Integer port = null;
        String username = "";
        String password = "";
        String upper = rest.toUpperCase(Locale.ROOT);
        int portIndex = upper.indexOf(" PORT ");
        if (portIndex != -1) {
            String afterPort = rest.substring(portIndex + 6).trim();
            port = Integer.parseInt(extractIdentifierToken(afterPort));
        }
        int userIndex = upper.indexOf(" USER ");
        if (userIndex != -1) {
            String afterUser = rest.substring(userIndex + 6).trim();
            username = extractQuotedOrIdentifier(afterUser);
        }
        int pwdIndex = upper.indexOf(" IDENTIFIED BY ");
        if (pwdIndex != -1) {
            password = extractQuotedOrIdentifier(rest.substring(pwdIndex + " IDENTIFIED BY ".length()).trim());
        }
        return new SqlCommand.Connect(server, port, username, password);
    }

    private String extractQuotedOrIdentifier(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("'") || trimmed.startsWith("‘")) {
            char quote = trimmed.charAt(0);
            char endQuote = quote == '‘' ? '’' : quote;
            int end = trimmed.indexOf(endQuote, 1);
            if (end > 0) {
                return trimmed.substring(1, end);
            }
        }
        return stripIdentifierQuotes(extractIdentifierToken(trimmed));
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

    private record InsertParts(String tableName, List<String> columns, List<Object> values) {}

    private record SelectParts(String tableName, List<String> projection, SqlCommand.FilterExpression filters,
                               List<SqlCommand.OrderBy> orderBy, SqlCommand.Limit limit) {}

    private record UpdateParts(String tableName, Map<String, Object> values, SqlCommand.FilterExpression filters) {}

    private record DeleteParts(String tableName, SqlCommand.FilterExpression filters) {}

    private record AlterTableParts(String tableName, SqlCommand.AlterAction action, ColumnDefinition column) {}

    private record ReplaceColumnsParts(String tableName, List<ColumnDefinition> columns) {}
}



