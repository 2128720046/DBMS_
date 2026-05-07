package com.dbms.backend.modules.index.domain;

import java.util.List;
import java.util.Map;

/**
 * 索引模块存储网关。
 * <p>
 * 该接口对应验收要求 3.6 和 3.12.9，负责索引描述文件 .tid 与索引数据文件 .ix
 * 的生命周期管理。后续实现应放在 {@code modules.index.infrastructure}，SQL 解析和
 * REST 接口只依赖本接口，避免直接耦合二进制文件格式。
 * </p>
 */
public interface IndexGateway {

    /**
     * 创建索引并写入索引描述文件。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @param indexName  索引名称
     * @param columns    索引字段列表，验收要求当前最多支持两个字段
     * @param unique     是否唯一索引
     * @param ascending  是否升序
     */
    void createIndex(String schemaName, String tableName, String indexName,
                     List<String> columns, boolean unique, boolean ascending);

    /**
     * 删除指定索引，并清理对应 .ix 索引数据文件。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @param indexName  索引名称
     */
    void dropIndex(String schemaName, String tableName, String indexName);

    /**
     * 查询指定表上的索引定义。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @return 索引元数据列表，字段包括 name、columns、unique、ascending、indexFile 等
     */
    List<Map<String, Object>> listIndexes(String schemaName, String tableName);

    /**
     * 重建指定索引的数据文件。
     *
     * @param schemaName 数据库名称
     * @param tableName  表名
     * @param indexName  索引名称
     */
    void rebuildIndex(String schemaName, String tableName, String indexName);
}
