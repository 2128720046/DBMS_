package com.dbms.backend.application;

import com.dbms.backend.domain.DatabaseDomainService;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表维护应用服务，提供索引和约束的管理功能。
 * <p>
 * 包括索引的创建、删除、重建和列表查询，以及约束的列表查询、
 * 完整性检查和删除操作。通过 JDBC 的 DatabaseMetaData 获取元数据信息。
 * </p>
 *
 * @author DBMS Team
 */
@Service
public class TableMaintenanceApplicationService {

    /** 数据库领域服务，用于名称校验和规范化 */
    private final DatabaseDomainService domainService;

    /** Spring JDBC 模板，用于执行 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造方法。
     *
     * @param domainService 数据库领域服务
     * @param jdbcTemplate  Spring JDBC 模板
     */
    public TableMaintenanceApplicationService(DatabaseDomainService domainService, JdbcTemplate jdbcTemplate) {
        this.domainService = domainService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 列出指定表的所有索引。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @return 索引列表，每个元素包含索引名称、是否唯一和包含的列名
     */
    public List<Map<String, Object>> listIndexes(String databaseName, String tableName) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, Map<String, Object>> indexes = new LinkedHashMap<>();
            try (ResultSet resultSet = metaData.getIndexInfo(null, databaseName.toUpperCase(), tableName.toUpperCase(), false, false)) {
                while (resultSet.next()) {
                    String indexName = resultSet.getString("INDEX_NAME");
                    String columnName = resultSet.getString("COLUMN_NAME");
                    if (indexName == null || columnName == null) {
                        continue;
                    }
                    boolean unique = !resultSet.getBoolean("NON_UNIQUE");
                    Map<String, Object> index = indexes.get(indexName);
                    if (index == null) {
                        index = new LinkedHashMap<>();
                        index.put("name", indexName);
                        index.put("unique", unique);
                        index.put("columns", new ArrayList<String>());
                        indexes.put(indexName, index);
                    }
                    @SuppressWarnings("unchecked")
                    List<String> columns = (List<String>) index.get("columns");
                    if (!columns.contains(columnName)) {
                        columns.add(columnName);
                    }
                }
            }
            return new ArrayList<>(indexes.values());
        });
    }

    /**
     * 在指定表的指定列上创建索引。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param indexName    索引名
     * @param unique       是否唯一索引
     * @param columns      索引列列表
     */
    public void createIndex(String databaseName, String tableName, String indexName, boolean unique, List<String> columns) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        domainService.validateIdentifier(indexName, "索引名");
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("索引列不能为空");
        }
        List<String> quotedColumns = columns.stream().map(column -> domainService.quoteIdentifier(column, "列名")).toList();
        String sql = "CREATE " + (unique ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                + domainService.quoteIdentifier(indexName, "索引名") + " ON "
                + domainService.quoteIdentifier(databaseName, "数据库名") + "."
                + domainService.quoteIdentifier(tableName, "表名")
                + " (" + String.join(", ", quotedColumns) + ")";
        jdbcTemplate.execute(sql);
    }

    /**
     * 删除指定索引。
     *
     * @param databaseName 数据库名称
     * @param indexName    索引名
     */
    public void dropIndex(String databaseName, String indexName) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(indexName, "索引名");
        String sql = "DROP INDEX IF EXISTS " + domainService.quoteIdentifier(indexName, "索引名");
        jdbcTemplate.execute(sql);
    }

    /**
     * 重建指定索引（先删除再创建）。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param indexName    索引名
     */
    public void rebuildIndex(String databaseName, String tableName, String indexName) {
        List<Map<String, Object>> indexes = listIndexes(databaseName, tableName);
        Map<String, Object> match = indexes.stream()
                .filter(item -> indexName.equalsIgnoreCase(String.valueOf(item.get("name"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("索引不存在"));
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) match.get("columns");
        boolean unique = Boolean.TRUE.equals(match.get("unique"));
        dropIndex(databaseName, indexName);
        createIndex(databaseName, tableName, indexName, unique, columns);
    }

    /**
     * 列出指定表的所有约束。
     * <p>
     * 包括主键约束、唯一约束、外键约束和 NOT NULL 约束。
     * 通过 JDBC DatabaseMetaData 获取元数据信息。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @return 约束列表，每个元素包含名称、类型、列名（外键还包含引用信息）
     */
    public List<Map<String, Object>> listConstraints(String databaseName, String tableName) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        return jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            List<Map<String, Object>> constraints = new ArrayList<>();

            // 获取主键约束
            Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
            try (ResultSet resultSet = metaData.getPrimaryKeys(null, databaseName.toUpperCase(), tableName.toUpperCase())) {
                while (resultSet.next()) {
                    String name = resultSet.getString("PK_NAME");
                    if (name == null || name.isBlank()) {
                        name = "PRIMARY_KEY";
                    }
                    primaryKeys.computeIfAbsent(name, key -> new ArrayList<>()).add(resultSet.getString("COLUMN_NAME"));
                }
            }
            primaryKeys.forEach((name, columns) -> constraints.add(buildConstraintItem(name, "PRIMARY KEY", columns)));

            // 获取唯一索引（排除主键）
            for (Map<String, Object> index : listIndexes(databaseName, tableName)) {
                if (!Boolean.TRUE.equals(index.get("unique"))) {
                    continue;
                }
                String name = String.valueOf(index.get("name"));
                if (name.startsWith("PRIMARY_KEY") || name.equalsIgnoreCase("PRIMARY_KEY")) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<String> columns = (List<String>) index.get("columns");
                constraints.add(buildConstraintItem(name, "UNIQUE", columns));
            }

            // 获取外键约束
            Map<String, List<String>> foreignKeys = new LinkedHashMap<>();
            Map<String, String> fkTargets = new HashMap<>();
            try (ResultSet resultSet = metaData.getImportedKeys(null, databaseName.toUpperCase(), tableName.toUpperCase())) {
                while (resultSet.next()) {
                    String name = resultSet.getString("FK_NAME");
                    if (name == null || name.isBlank()) {
                        name = "FK_" + resultSet.getString("FKCOLUMN_NAME");
                    }
                    foreignKeys.computeIfAbsent(name, key -> new ArrayList<>()).add(resultSet.getString("FKCOLUMN_NAME"));
                    fkTargets.putIfAbsent(name, resultSet.getString("PKTABLE_NAME") + "." + resultSet.getString("PKCOLUMN_NAME"));
                }
            }
            foreignKeys.forEach((name, columns) -> {
                Map<String, Object> item = buildConstraintItem(name, "FOREIGN KEY", columns);
                item.put("reference", fkTargets.get(name));
                constraints.add(item);
            });

            // 获取 NOT NULL 约束
            try (ResultSet resultSet = metaData.getColumns(null, databaseName.toUpperCase(), tableName.toUpperCase(), null)) {
                while (resultSet.next()) {
                    if (resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) {
                        String columnName = resultSet.getString("COLUMN_NAME");
                        constraints.add(buildConstraintItem("NN_" + columnName, "NOT NULL", List.of(columnName)));
                    }
                }
            }
            return constraints;
        });
    }

    /**
     * 检查指定表的约束完整性和数据一致性。
     * <p>
     * 对于 NOT NULL 约束，检查是否存在空值；对于唯一约束和主键约束，检查是否存在重复数据。
     * </p>
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @return 检查结果，包含是否通过、问题列表和检查项数量
     */
    public Map<String, Object> checkConstraints(String databaseName, String tableName) {
        List<Map<String, Object>> constraints = listConstraints(databaseName, tableName);
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Map<String, Object> constraint : constraints) {
            String type = String.valueOf(constraint.get("type"));
            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) constraint.get("columns");
            if (columns == null || columns.isEmpty()) {
                continue;
            }
            if ("NOT NULL".equals(type)) {
                String column = columns.get(0);
                Integer nullCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + qualifiedTable(databaseName, tableName) + " WHERE " + domainService.quoteIdentifier(column, "列名") + " IS NULL",
                        Integer.class
                );
                if (nullCount != null && nullCount > 0) {
                    issues.add(Map.of("constraint", constraint.get("name"), "message", column + " 存在空值", "count", nullCount));
                }
                continue;
            }
            String projection = String.join(", ", columns.stream().map(column -> domainService.quoteIdentifier(column, "列名")).toList());
            String sql = "SELECT " + projection + ", COUNT(*) AS DUP_COUNT FROM " + qualifiedTable(databaseName, tableName)
                    + " GROUP BY " + projection + " HAVING COUNT(*) > 1";
            List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(sql);
            if (!duplicates.isEmpty()) {
                issues.add(Map.of("constraint", constraint.get("name"), "message", type + " 存在重复数据", "count", duplicates.size()));
            }
        }
        return Map.of(
                "passed", issues.isEmpty(),
                "issues", issues,
                "checked", constraints.size()
        );
    }

    /**
     * 删除指定约束。
     * <p>
     * 支持删除主键约束、NOT NULL 约束和其他类型的约束。
     * </p>
     *
     * @param databaseName   数据库名称
     * @param tableName      表名
     * @param constraintName 约束名称
     */
    public void dropConstraint(String databaseName, String tableName, String constraintName) {
        domainService.validateDatabaseName(databaseName);
        domainService.validateIdentifier(tableName, "表名");
        if (constraintName == null || constraintName.isBlank()) {
            throw new IllegalArgumentException("约束名称不能为空");
        }
        Map<String, Object> target = listConstraints(databaseName, tableName).stream()
                .filter(item -> constraintName.equalsIgnoreCase(String.valueOf(item.get("name"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("约束不存在"));
        String type = String.valueOf(target.get("type"));
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) target.get("columns");
        if ("PRIMARY KEY".equals(type)) {
            jdbcTemplate.execute("ALTER TABLE " + qualifiedTable(databaseName, tableName) + " DROP PRIMARY KEY");
            return;
        }
        if ("NOT NULL".equals(type)) {
            String column = columns.get(0);
            jdbcTemplate.execute("ALTER TABLE " + qualifiedTable(databaseName, tableName)
                    + " ALTER COLUMN " + domainService.quoteIdentifier(column, "列名") + " SET NULL");
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + qualifiedTable(databaseName, tableName)
                + " DROP CONSTRAINT " + domainService.quoteIdentifier(constraintName, "约束名"));
    }

    /**
     * 构建约束项 Map。
     *
     * @param name    约束名称
     * @param type    约束类型（PRIMARY KEY、UNIQUE、FOREIGN KEY、NOT NULL）
     * @param columns 约束包含的列名列表
     * @return 约束项 Map
     */
    private Map<String, Object> buildConstraintItem(String name, String type, List<String> columns) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("type", type);
        item.put("columns", new ArrayList<>(columns));
        return item;
    }

    /**
     * 构建完全限定的表名（database.table）。
     *
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @return 完全限定的表名字符串
     */
    private String qualifiedTable(String databaseName, String tableName) {
        return domainService.quoteIdentifier(databaseName, "数据库名") + "." + domainService.quoteIdentifier(tableName, "表名");
    }
}