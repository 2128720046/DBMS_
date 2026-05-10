package com.dbms.backend.modules.integrity.infrastructure;

import com.dbms.backend.modules.integrity.domain.IntegrityGateway;
import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.core.storage.io.BinaryIoUtils;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 完整性约束网关占位实现。
 */
@Repository
public class TodoIntegrityGatewayImpl implements IntegrityGateway {

    private static final String IGNORE_OFFSET_KEY = "__dbms_ignoreOffset";

    private static final int FIELD_BLOCK_SIZE = 160;
    private static final int TIC_BLOCK_SIZE = 516;

    // type code mapping for .tic
    private static final int TYPE_RAW = 0;
    private static final int TYPE_PRIMARY_KEY = 1;
    private static final int TYPE_FOREIGN_KEY = 2;
    private static final int TYPE_UNIQUE = 3;
    private static final int TYPE_NOT_NULL = 4;
    private static final int TYPE_DEFAULT = 5;
    private static final int TYPE_IDENTITY = 6;
    private static final int TYPE_CHECK = 7;

    private static final Pattern CHECK_PATTERN = Pattern.compile(
            "(?i)CHECK\\s*\\(?\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(=|!=|>=|<=|>|<)\\s*([^\\)]+)\\s*\\)?");
    private static final Pattern FK_PATTERN = Pattern.compile(
            "(?i)FOREIGN\\s+KEY\\s*\\(([^\\)]+)\\)\\s+REFERENCES\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^\\)]+)\\)");

    private String normalizeType(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase().replace(' ', '_');
    }

    @Override
    public List<Map<String, Object>> listConstraints(String schemaName, String tableName) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 1) 从 .tdf 推导隐式约束（NOT NULL / PK / UNIQUE）
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        if (tdfFile.exists()) {
            try {
                results.addAll(readImplicitConstraintsFromTdf(tdfFile));
            } catch (Exception ignored) {
            }
        }

        // 2) 从 .tic 读取显式约束
        File ticFile = new File(schemaDir, tableName + ".tic");
        if (!ticFile.exists()) {
            return results;
        }
        try (RandomAccessFile raf = new RandomAccessFile(ticFile, "r")) {
            long length = raf.length();
            long pos = 0;
            while (pos + TIC_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                String field = BinaryIoUtils.readFixedString(raf, 128);
                int type = raf.readInt();
                String param = BinaryIoUtils.readFixedString(raf, 256);
                if (!name.isEmpty()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", name);
                    row.put("type", typeName(type));
                    row.put("column", field);
                    row.put("parameter", param);
                    results.add(row);
                }
                pos += TIC_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("读取 .tic 失败: " + e.getMessage(), e);
        }
        return results;
    }

    @Override
    public void saveConstraint(String schemaName, String tableName, String constraintName,
                               String columnName, String type, String parameter) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        if (!schemaDir.exists()) {
            throw new IllegalArgumentException("数据库不存在: " + schemaName);
        }
        File ticFile = new File(schemaDir, tableName + ".tic");
        ensureTicPlaceholder(ticFile);

        ParsedConstraint parsed = normalizeRawConstraint(columnName, type, parameter);
        int typeCode = typeCode(parsed.type);

        try (RandomAccessFile raf = new RandomAccessFile(ticFile, "rw")) {
            long length = raf.length();
            long pos = 0;
            long emptyPos = -1;
            while (pos + TIC_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(constraintName)) {
                    writeTicBlock(raf, pos, constraintName, parsed.column, typeCode, parsed.parameter);
                    return;
                }
                if (name.isEmpty() && emptyPos == -1) {
                    emptyPos = pos;
                }
                pos += TIC_BLOCK_SIZE;
            }
            long writePos = emptyPos != -1 ? emptyPos : length;
            writeTicBlock(raf, writePos, constraintName, parsed.column, typeCode, parsed.parameter);
        } catch (IOException e) {
            throw new RuntimeException("写入 .tic 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void dropConstraint(String schemaName, String tableName, String constraintName) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File ticFile = new File(schemaDir, tableName + ".tic");
        if (!ticFile.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(ticFile, "rw")) {
            long length = raf.length();
            long pos = 0;
            while (pos + TIC_BLOCK_SIZE <= length) {
                raf.seek(pos);
                String name = BinaryIoUtils.readFixedString(raf, 128);
                if (!name.isEmpty() && name.equalsIgnoreCase(constraintName)) {
                    writeTicBlock(raf, pos, "", "", TYPE_RAW, "");
                    return;
                }
                pos += TIC_BLOCK_SIZE;
            }
        } catch (IOException e) {
            throw new RuntimeException("更新 .tic 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> validateRow(String schemaName, String tableName, Map<String, Object> row) {
        // 行级校验：实体完整性、参照完整性、用户定义完整性
        if (row == null) {
            return List.of();
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> constraints = listConstraints(schemaName, tableName);
        Long ignoreOffset = readIgnoreOffset(row);
        for (Map<String, Object> c : constraints) {
            String type = normalizeType(Objects.toString(c.get("type"), ""));
            String column = Objects.toString(c.get("column"), "");
            if (column.isBlank()) {
                continue;
            }
            Object value = getValueIgnoreCase(row, column);
            if ("NOT_NULL".equalsIgnoreCase(type)) {
                if (value == null || (value instanceof String s && s.isBlank())) {
                    issues.add(issue("NOT_NULL", column, "值不能为空"));
                }
            }
            if ("PRIMARY_KEY".equalsIgnoreCase(type) || "UNIQUE".equalsIgnoreCase(type)) {
                if (value == null || (value instanceof String s && s.isBlank())) {
                    if ("PRIMARY_KEY".equalsIgnoreCase(type)) {
                        issues.add(issue("PRIMARY_KEY", column, "主键不能为空"));
                    }
                    continue;
                }
                if (existsDuplicate(schemaName, tableName, column, value, ignoreOffset)) {
                    issues.add(issue(type.toUpperCase(), column, "存在重复值: " + value));
                }
            }
            if ("CHECK".equalsIgnoreCase(type)) {
                String parameter = Objects.toString(c.get("parameter"), "");
                CheckExpression expr = parseCheckExpression(parameter, column);
                if (expr == null) {
                    issues.add(issue("CHECK", column, "CHECK 约束解析失败: " + parameter));
                } else if (!matchCheckExpression(row, expr)) {
                    issues.add(issue("CHECK", expr.column, "不满足检查条件: " + expr.raw));
                }
            }
            if ("FOREIGN_KEY".equalsIgnoreCase(type)) {
                String parameter = Objects.toString(c.get("parameter"), "");
                ForeignKeyDefinition fk = parseForeignKey(parameter, column);
                if (fk == null) {
                    issues.add(issue("FOREIGN_KEY", column, "外键解析失败: " + parameter));
                    continue;
                }
                Object fkValue = getValueIgnoreCase(row, fk.localColumn);
                if (fkValue == null || (fkValue instanceof String s && s.isBlank())) {
                    continue;
                }
                if (!existsReference(schemaName, fk.refTable, fk.refColumn, fkValue)) {
                    issues.add(issue("FOREIGN_KEY", fk.localColumn,
                            "外键引用不存在: " + fk.refTable + "." + fk.refColumn + " = " + fkValue));
                }
            }
        }
        return issues;
    }

    @Override
    public List<Map<String, Object>> validateTable(String schemaName, String tableName) {
        // 全表校验：用于后台检查或批量校验
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> constraints = listConstraints(schemaName, tableName);

        // 全表校验：NOT NULL / UNIQUE / PRIMARY KEY / CHECK / FOREIGN KEY
        for (Map<String, Object> c : constraints) {
            String type = normalizeType(Objects.toString(c.get("type"), ""));
            String column = Objects.toString(c.get("column"), "");
            if (column.isBlank()) {
                continue;
            }
            if ("NOT_NULL".equalsIgnoreCase(type)) {
                issues.addAll(checkNotNull(schemaName, tableName, column));
            }
            if ("PRIMARY_KEY".equalsIgnoreCase(type) || "UNIQUE".equalsIgnoreCase(type)) {
                issues.addAll(checkUnique(schemaName, tableName, column, "PRIMARY_KEY".equalsIgnoreCase(type)));
            }
            if ("CHECK".equalsIgnoreCase(type)) {
                String parameter = Objects.toString(c.get("parameter"), "");
                CheckExpression expr = parseCheckExpression(parameter, column);
                if (expr != null) {
                    issues.addAll(checkExpression(schemaName, tableName, expr));
                }
            }
            if ("FOREIGN_KEY".equalsIgnoreCase(type)) {
                String parameter = Objects.toString(c.get("parameter"), "");
                ForeignKeyDefinition fk = parseForeignKey(parameter, column);
                if (fk != null) {
                    issues.addAll(checkForeignKey(schemaName, tableName, fk));
                }
            }
        }

        return issues;
    }

    @Override
    public List<Map<String, Object>> validateDelete(String schemaName, String tableName, Map<String, Object> filters) {
        // 删除校验：参照完整性（是否被外键引用）
        List<Map<String, Object>> issues = new ArrayList<>();
        // 仅检查参照完整性：是否存在其他表外键引用当前表
        List<ForeignKeyDefinition> inbound = listInboundForeignKeys(schemaName, tableName);
        if (inbound.isEmpty()) {
            return issues;
        }
        List<Map<String, Object>> targetRows = findRows(schemaName, tableName, filters);
        for (Map<String, Object> row : targetRows) {
            for (ForeignKeyDefinition fk : inbound) {
                Object value = getValueIgnoreCase(row, fk.refColumn);
                if (value == null || (value instanceof String s && s.isBlank())) {
                    continue;
                }
                if (existsReference(schemaName, fk.localTable, fk.localColumn, value)) {
                    issues.add(issue("FOREIGN_KEY", fk.localColumn,
                            "存在外键引用: " + fk.localTable + "." + fk.localColumn + " = " + value));
                }
            }
        }
        return issues;
    }

    private record ParsedConstraint(String column, String type, String parameter) {}

    private record ForeignKeyDefinition(String localTable, String localColumn, String refTable, String refColumn) {}

    private record CheckExpression(String column, String operator, String literal, String raw) {}

    private ParsedConstraint normalizeRawConstraint(String columnName, String type, String parameter) {
        String safeColumn = columnName == null ? "" : columnName;
        String safeType = type == null ? "RAW" : type.trim();
        String safeParam = parameter == null ? "" : parameter;
        if (!"RAW".equalsIgnoreCase(safeType)) {
            return new ParsedConstraint(safeColumn, safeType.toUpperCase(), safeParam);
        }

        String trimmed = safeParam.trim();
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("UNIQUE")) {
            return new ParsedConstraint(extractFirstColumnInParen(trimmed), "UNIQUE", trimmed);
        }
        if (upper.startsWith("PRIMARY KEY")) {
            return new ParsedConstraint(extractFirstColumnInParen(trimmed), "PRIMARY_KEY", trimmed);
        }
        if (upper.startsWith("FOREIGN KEY")) {
            return new ParsedConstraint(extractFirstColumnInParen(trimmed), "FOREIGN_KEY", trimmed);
        }
        if (upper.startsWith("CHECK")) {
            return new ParsedConstraint(safeColumn, "CHECK", trimmed);
        }
        if (upper.startsWith("NOT NULL")) {
            return new ParsedConstraint(safeColumn, "NOT_NULL", trimmed);
        }
        return new ParsedConstraint(safeColumn, "RAW", trimmed);
    }

    private String extractFirstColumnInParen(String text) {
        int start = text.indexOf('(');
        int end = text.indexOf(')');
        if (start >= 0 && end > start) {
            String inner = text.substring(start + 1, end).trim();
            int comma = inner.indexOf(',');
            String first = (comma == -1 ? inner : inner.substring(0, comma)).trim();
            // 去掉可能的引号
            if ((first.startsWith("`") && first.endsWith("`")) || (first.startsWith("\"") && first.endsWith("\""))) {
                first = first.substring(1, first.length() - 1);
            }
            return first;
        }
        return "";
    }

    private void ensureTicPlaceholder(File ticFile) {
        try {
            if (!ticFile.exists()) {
                ticFile.getParentFile().mkdirs();
                ticFile.createNewFile();
            }
            try (RandomAccessFile raf = new RandomAccessFile(ticFile, "rw")) {
                if (raf.length() > 0) {
                    return;
                }
                // placeholder
                BinaryIoUtils.writeFixedString(raf, "", 128);
                BinaryIoUtils.writeFixedString(raf, "", 128);
                raf.writeInt(TYPE_RAW);
                BinaryIoUtils.writeFixedString(raf, "", 256);
            }
        } catch (IOException e) {
            throw new RuntimeException("初始化 .tic 失败: " + e.getMessage(), e);
        }
    }

    private void writeTicBlock(RandomAccessFile raf, long pos,
                               String name, String column, int type, String parameter) throws IOException {
        raf.seek(pos);
        BinaryIoUtils.writeFixedString(raf, name, 128);
        BinaryIoUtils.writeFixedString(raf, column == null ? "" : column, 128);
        raf.writeInt(type);
        BinaryIoUtils.writeFixedString(raf, parameter == null ? "" : parameter, 256);
    }

    private int typeCode(String type) {
        if (type == null) {
            return TYPE_RAW;
        }
        return switch (normalizeType(type)) {
            case "PRIMARY_KEY", "PK" -> TYPE_PRIMARY_KEY;
            case "FOREIGN_KEY", "FK" -> TYPE_FOREIGN_KEY;
            case "UNIQUE", "UQ" -> TYPE_UNIQUE;
            case "NOT_NULL", "NN" -> TYPE_NOT_NULL;
            case "DEFAULT" -> TYPE_DEFAULT;
            case "IDENTITY" -> TYPE_IDENTITY;
            case "CHECK" -> TYPE_CHECK;
            default -> TYPE_RAW;
        };
    }

    private String typeName(int type) {
        return switch (type) {
            case TYPE_PRIMARY_KEY -> "PRIMARY KEY";
            case TYPE_FOREIGN_KEY -> "FOREIGN KEY";
            case TYPE_UNIQUE -> "UNIQUE";
            case TYPE_NOT_NULL -> "NOT NULL";
            case TYPE_DEFAULT -> "DEFAULT";
            case TYPE_IDENTITY -> "IDENTITY";
            case TYPE_CHECK -> "CHECK";
            default -> "RAW";
        };
    }

    private List<Map<String, Object>> readImplicitConstraintsFromTdf(File tdfFile) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long length = raf.length();
            long pos = 0;
            while (pos + FIELD_BLOCK_SIZE <= length) {
                raf.seek(pos);
                raf.readInt();
                String name = BinaryIoUtils.readFixedString(raf, 128);
                raf.readInt();
                raf.readInt();
                BinaryIoUtils.readDateTime(raf);
                int integrities = raf.readInt();
                if (!name.isEmpty()) {
                    if ((integrities & 1) != 0) {
                        results.add(implicit("NOT_NULL", name));
                    }
                    if ((integrities & 2) != 0) {
                        results.add(implicit("PRIMARY_KEY", name));
                    }
                    if ((integrities & 4) != 0) {
                        results.add(implicit("UNIQUE", name));
                    }
                }
                pos += FIELD_BLOCK_SIZE;
            }
        }
        return results;
    }

    private Map<String, Object> implicit(String type, String column) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "IMPLICIT_" + type + "_" + column);
        row.put("type", type.replace('_', ' '));
        row.put("column", column);
        row.put("parameter", "");
        return row;
    }

    private Map<String, Object> issue(String type, String column, String message) {
        Map<String, Object> row = new HashMap<>();
        row.put("type", type);
        row.put("column", column);
        row.put("message", message);
        return row;
    }

    private Long readIgnoreOffset(Map<String, Object> row) {
        Object raw = row.get(IGNORE_OFFSET_KEY);
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean existsDuplicate(String schemaName, String tableName, String column, Object value, Long ignoreOffset) {
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        File trdFile = new File(schemaDir, tableName + ".trd");
        if (!tdfFile.exists() || !trdFile.exists()) {
            return false;
        }
        try {
            List<FieldMeta> metas = parseTdf(tdfFile);
            FieldMeta target = metas.stream().filter(m -> m.name.equalsIgnoreCase(column)).findFirst().orElse(null);
            if (target == null) {
                return false;
            }
            int recordLength = 4;
            for (FieldMeta m : metas) recordLength += m.length;
            String needle = Objects.toString(value, "");
            int count = 0;
            try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
                long fileLength = raf.length();
                long pos = 0;
                while (pos + recordLength <= fileLength) {
                    raf.seek(pos);
                    int status = raf.readInt();
                    if (status == 1) {
                        if (ignoreOffset != null && ignoreOffset == pos) {
                            pos += recordLength;
                            continue;
                        }
                        raf.seek(pos + 4L + target.offset);
                        Object current = readValue(raf, target);
                        if (needle.equals(Objects.toString(current, ""))) {
                            count++;
                            if (count > 0) {
                                return true;
                            }
                        }
                    }
                    pos += recordLength;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // =============== 用户定义 CHECK 校验 ===============

    private CheckExpression parseCheckExpression(String parameter, String fallbackColumn) {
        // 解析简单 CHECK 表达式：CHECK (col op literal)
        if (parameter == null || parameter.isBlank()) {
            return null;
        }
        Matcher matcher = CHECK_PATTERN.matcher(parameter.trim());
        if (!matcher.find()) {
            return null;
        }
        String column = matcher.group(1) != null ? matcher.group(1).trim() : fallbackColumn;
        if (column == null || column.isBlank()) {
            column = fallbackColumn;
        }
        if (column != null) {
            column = column.trim();
            if (!column.isEmpty()) {
                column = column.toUpperCase(Locale.ROOT);
            }
        }
        String operator = matcher.group(2) != null ? matcher.group(2).trim() : "=";
        String literal = matcher.group(3) != null ? matcher.group(3).trim() : "";
        return new CheckExpression(column, operator, literal, parameter.trim());
    }

    private boolean matchCheckExpression(Map<String, Object> row, CheckExpression expr) {
        Object value = getValueIgnoreCase(row, expr.column);
        Object literal = parseLiteral(expr.literal);
        int cmp = compareValue(value, literal);
        return switch (expr.operator) {
            case "=" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    private List<Map<String, Object>> checkExpression(String schemaName, String tableName, CheckExpression expr) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> rows = findRows(schemaName, tableName, Map.of());
        for (Map<String, Object> row : rows) {
            if (!matchCheckExpression(row, expr)) {
                issues.add(issue("CHECK", expr.column, "不满足检查条件: " + expr.raw));
            }
        }
        return issues;
    }

    // =============== 参照完整性校验 ===============

    private ForeignKeyDefinition parseForeignKey(String parameter, String fallbackColumn) {
        // 解析外键格式：FOREIGN KEY (col) REFERENCES ref_table(ref_col)
        if (parameter == null || parameter.isBlank()) {
            return null;
        }
        Matcher matcher = FK_PATTERN.matcher(parameter.trim());
        if (!matcher.find()) {
            return null;
        }
        String localColumn = matcher.group(1).trim();
        String refTable = matcher.group(2).trim();
        String refColumn = matcher.group(3).trim();
        if (localColumn.isBlank()) {
            localColumn = fallbackColumn;
        }
        localColumn = normalizeIdentifierName(localColumn);
        refTable = normalizeIdentifierName(refTable);
        refColumn = normalizeIdentifierName(refColumn);
        return new ForeignKeyDefinition("", localColumn, refTable, refColumn);
    }

    private boolean existsReference(String schemaName, String tableName, String column, Object value) {
        // 判断引用表中是否存在对应值
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        File trdFile = new File(schemaDir, tableName + ".trd");
        if (!tdfFile.exists() || !trdFile.exists()) {
            return false;
        }
        try {
            List<FieldMeta> metas = parseTdf(tdfFile);
            FieldMeta target = metas.stream().filter(m -> m.name.equalsIgnoreCase(column)).findFirst().orElse(null);
            if (target == null) {
                return false;
            }
            int recordLength = 4;
            for (FieldMeta m : metas) recordLength += m.length;
            String needle = Objects.toString(value, "");
            try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
                long fileLength = raf.length();
                long pos = 0;
                while (pos + recordLength <= fileLength) {
                    raf.seek(pos);
                    int status = raf.readInt();
                    if (status == 1) {
                        raf.seek(pos + 4L + target.offset);
                        Object current = readValue(raf, target);
                        if (needle.equals(Objects.toString(current, ""))) {
                            return true;
                        }
                    }
                    pos += recordLength;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private List<Map<String, Object>> checkForeignKey(String schemaName, String tableName, ForeignKeyDefinition fk) {
        // 全表外键一致性检查
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> rows = findRows(schemaName, tableName, Map.of());
        for (Map<String, Object> row : rows) {
            Object value = getValueIgnoreCase(row, fk.localColumn);
            if (value == null || (value instanceof String s && s.isBlank())) {
                continue;
            }
            if (!existsReference(schemaName, fk.refTable, fk.refColumn, value)) {
                issues.add(issue("FOREIGN_KEY", fk.localColumn,
                        "外键引用不存在: " + fk.refTable + "." + fk.refColumn + " = " + value));
            }
        }
        return issues;
    }

    private List<ForeignKeyDefinition> listInboundForeignKeys(String schemaName, String refTable) {
        // 扫描 schema 下所有 .tic，找出指向 refTable 的外键
        List<ForeignKeyDefinition> inbound = new ArrayList<>();
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File[] tics = schemaDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".tic"));
        if (tics == null) {
            return inbound;
        }
        for (File tic : tics) {
            String localTable = tic.getName().replaceFirst("\\.tic$", "");
            try (RandomAccessFile raf = new RandomAccessFile(tic, "r")) {
                long length = raf.length();
                long pos = 0;
                while (pos + TIC_BLOCK_SIZE <= length) {
                    raf.seek(pos);
                    String name = BinaryIoUtils.readFixedString(raf, 128);
                    String field = BinaryIoUtils.readFixedString(raf, 128);
                    int type = raf.readInt();
                    String param = BinaryIoUtils.readFixedString(raf, 256);
                    if (!name.isEmpty() && typeName(type).equalsIgnoreCase("FOREIGN KEY")) {
                        ForeignKeyDefinition fk = parseForeignKey(param, field);
                        if (fk != null && fk.refTable.equalsIgnoreCase(refTable)) {
                            inbound.add(new ForeignKeyDefinition(localTable, fk.localColumn, fk.refTable, fk.refColumn));
                        }
                    }
                    pos += TIC_BLOCK_SIZE;
                }
            } catch (IOException ignored) {
            }
        }
        return inbound;
    }

    private List<Map<String, Object>> findRows(String schemaName, String tableName, Map<String, Object> filters) {
        // 基于全表扫描获取满足条件的行
        List<Map<String, Object>> results = new ArrayList<>();
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        File trdFile = new File(schemaDir, tableName + ".trd");
        if (!tdfFile.exists() || !trdFile.exists()) {
            return results;
        }
        try {
            List<FieldMeta> metas = parseTdf(tdfFile);
            int recordLength = 4;
            for (FieldMeta m : metas) recordLength += m.length;
            try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
                long fileLength = raf.length();
                long pos = 0;
                while (pos + recordLength <= fileLength) {
                    raf.seek(pos);
                    int status = raf.readInt();
                    if (status == 1) {
                        Map<String, Object> row = new HashMap<>();
                        for (FieldMeta meta : metas) {
                            raf.seek(pos + 4L + meta.offset);
                            row.put(meta.name, readValue(raf, meta));
                        }
                        if (matchFilters(row, filters)) {
                            results.add(row);
                        }
                    }
                    pos += recordLength;
                }
            }
        } catch (Exception ignored) {
        }
        return results;
    }

    private boolean matchFilters(Map<String, Object> row, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object expected = entry.getValue();
            Object actual = getValueIgnoreCase(row, entry.getKey());
            if (expected != null && !Objects.toString(actual, "").equals(Objects.toString(expected, ""))) {
                return false;
            }
        }
        return true;
    }

    private Object getValueIgnoreCase(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object value = row.get(key);
        if (value != null || row.containsKey(key)) {
            return value;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeIdentifierName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "" : trimmed.toUpperCase(Locale.ROOT);
    }

    private Object parseLiteral(String literal) {
        String text = literal == null ? "" : literal.trim();
        if (text.isEmpty()) {
            return "";
        }
        if ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\""))) {
            return text.substring(1, text.length() - 1);
        }
        if ("NULL".equalsIgnoreCase(text)) {
            return null;
        }
        if ("TRUE".equalsIgnoreCase(text) || "FALSE".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        try {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
        }
        return text;
    }

    private int compareValue(Object left, Object right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return Boolean.compare((Boolean) left, (Boolean) right);
        }
        return Objects.toString(left, "").compareTo(Objects.toString(right, ""));
    }

    private List<Map<String, Object>> checkNotNull(String schemaName, String tableName, String column) {
        List<Map<String, Object>> issues = new ArrayList<>();
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        File trdFile = new File(schemaDir, tableName + ".trd");
        if (!tdfFile.exists() || !trdFile.exists()) {
            return issues;
        }
        try {
            List<FieldMeta> metas = parseTdf(tdfFile);
            FieldMeta target = metas.stream().filter(m -> m.name.equalsIgnoreCase(column)).findFirst().orElse(null);
            if (target == null) {
                return issues;
            }
            int recordLength = 4;
            for (FieldMeta m : metas) recordLength += m.length;
            try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
                long fileLength = raf.length();
                long pos = 0;
                while (pos + recordLength <= fileLength) {
                    raf.seek(pos);
                    int status = raf.readInt();
                    if (status == 1) {
                        raf.seek(pos + 4L + target.offset);
                        Object current = readValue(raf, target);
                        if (current == null || (current instanceof String s && s.isBlank())) {
                            issues.add(issue("NOT_NULL", column, "发现空值记录(偏移=" + pos + ")"));
                        }
                    }
                    pos += recordLength;
                }
            }
        } catch (Exception ignored) {
        }
        return issues;
    }

    private List<Map<String, Object>> checkUnique(String schemaName, String tableName, String column, boolean primaryKey) {
        List<Map<String, Object>> issues = new ArrayList<>();
        File schemaDir = new File(StorageEngineConfig.getDATA_DIR() + File.separator + schemaName);
        File tdfFile = new File(schemaDir, tableName + ".tdf");
        File trdFile = new File(schemaDir, tableName + ".trd");
        if (!tdfFile.exists() || !trdFile.exists()) {
            return issues;
        }
        Set<String> seen = new HashSet<>();
        try {
            List<FieldMeta> metas = parseTdf(tdfFile);
            FieldMeta target = metas.stream().filter(m -> m.name.equalsIgnoreCase(column)).findFirst().orElse(null);
            if (target == null) {
                return issues;
            }
            int recordLength = 4;
            for (FieldMeta m : metas) recordLength += m.length;
            try (RandomAccessFile raf = new RandomAccessFile(trdFile, "r")) {
                long fileLength = raf.length();
                long pos = 0;
                while (pos + recordLength <= fileLength) {
                    raf.seek(pos);
                    int status = raf.readInt();
                    if (status == 1) {
                        raf.seek(pos + 4L + target.offset);
                        Object current = readValue(raf, target);
                        String key = Objects.toString(current, "");
                        if (primaryKey && key.isBlank()) {
                            issues.add(issue("PRIMARY_KEY", column, "主键为空(偏移=" + pos + ")"));
                        }
                        if (!key.isBlank() && !seen.add(key)) {
                            issues.add(issue(primaryKey ? "PRIMARY_KEY" : "UNIQUE", column, "重复值: " + key));
                        }
                    }
                    pos += recordLength;
                }
            }
        } catch (Exception ignored) {
        }
        return issues;
    }

    private static class FieldMeta {
        String name;
        int type;
        int param;
        int length;
        int offset;
    }

    private List<FieldMeta> parseTdf(File tdfFile) throws IOException {
        List<FieldMeta> metas = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(tdfFile, "r")) {
            long fileLength = raf.length();
            long pos = 0;
            int currentOffset = 0;
            while (pos + FIELD_BLOCK_SIZE <= fileLength) {
                raf.seek(pos);
                raf.readInt();
                String name = BinaryIoUtils.readFixedString(raf, 128);
                int type = raf.readInt();
                int param = raf.readInt();
                // skip mtime 16 + integrities 4

                FieldMeta meta = new FieldMeta();
                meta.name = name;
                meta.type = type;
                meta.param = param;
                meta.offset = currentOffset;

                int length;
                switch (type) {
                    case 1: length = 4; break;
                    case 2: length = 1; break;
                    case 3: length = 8; break;
                    case 5: length = 16; break;
                    case 4: default: length = param + 1; break;
                }
                int padding = length % 4;
                if (padding != 0) {
                    length += (4 - padding);
                }
                meta.length = length;
                currentOffset += length;
                metas.add(meta);
                pos += FIELD_BLOCK_SIZE;
            }
        }
        return metas;
    }

    private Object readValue(RandomAccessFile raf, FieldMeta meta) throws IOException {
        return switch (meta.type) {
            case 1 -> raf.readInt();
            case 2 -> raf.readByte() != 0;
            case 3 -> raf.readDouble();
            case 5 -> BinaryIoUtils.readDateTime(raf);
            default -> BinaryIoUtils.readFixedString(raf, meta.param + 1);
        };
    }
}
