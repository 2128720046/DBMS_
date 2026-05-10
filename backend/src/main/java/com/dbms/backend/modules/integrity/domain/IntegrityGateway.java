package com.dbms.backend.modules.integrity.domain;

import java.util.List;
import java.util.Map;

/**
 * 完整性模块存储网关。
 * <p>
 * 该接口对应验收要求 3.9、3.12.2 和 3.12.8，负责 CHECK、UNIQUE、NOT NULL、
 * DEFAULT、IDENTITY、PRIMARY KEY、FOREIGN KEY 等约束的描述、检查与报告。
 * </p>
 */
public interface IntegrityGateway {

    /**
     * 查询指定表的全部约束定义。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @return 约束元数据列表，字段包括 name、type、column、parameter 等
     */
    List<Map<String, Object>> listConstraints(String schemaName, String tableName);

    /**
     * 新增或覆盖一个表级/字段级完整性约束。
     *
     * @param schemaName     数据库名称
     * @param tableName      表名
     * @param constraintName 约束名称
     * @param columnName     约束关联字段
     * @param type           约束类型，如 PRIMARY_KEY、UNIQUE、NOT_NULL、CHECK
     * @param parameter      约束参数，如默认值、CHECK 表达式或外键引用信息
     */
    void saveConstraint(String schemaName, String tableName, String constraintName,
                        String columnName, String type, String parameter);

    /**
     * 删除指定完整性约束。
     *
     * @param schemaName     数据库名称
     * @param tableName      表名
     * @param constraintName 约束名称
     */
    void dropConstraint(String schemaName, String tableName, String constraintName);

    /**
     * 对即将写入或更新的记录执行约束检查。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @param row        待校验记录
     * @return 违规列表；空列表表示校验通过
     */
    List<Map<String, Object>> validateRow(String schemaName, String tableName, Map<String, Object> row);

    /**
     * 对整张表执行完整性校验。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @return 校验问题列表；空列表表示全表校验通过
     */
    List<Map<String, Object>> validateTable(String schemaName, String tableName);

    /**
     * 校验删除操作是否违反参照完整性。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @param filters    删除条件（字段名与值的映射）
     * @return 违规列表；空列表表示可安全删除
     */
    List<Map<String, Object>> validateDelete(String schemaName, String tableName, Map<String, Object> filters);
}
