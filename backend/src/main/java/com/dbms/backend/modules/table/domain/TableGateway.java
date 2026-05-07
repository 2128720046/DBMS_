package com.dbms.backend.modules.table.domain;

import com.dbms.backend.modules.table.dto.ColumnDefinition;

import java.util.List;
import java.util.Map;

/**
 * SPI for table lifecycle storage operations.
 */
/**
 * 表网关接口，定义数据表的创建、修改、删除和查询操作。
 * <p>
 * 作为领域层的 SPI (Service Provider Interface)，由基础设施层实现具体的存储逻辑。
 * 用于解耦领域层与底层表存储细节。
 * </p>
 *
 * @author DBMS Team
 */
public interface TableGateway {

    /**
     * 创建数据表。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @param columns    列定义列表
     */
    void createTable(String schemaName, String tableName, List<ColumnDefinition> columns);

    /**
     * 修改表结构。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @param columns    新的列定义列表
     */
    void alterTableStructure(String schemaName, String tableName, List<ColumnDefinition> columns);

    /**
     * 删除数据表。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     */
    void dropTable(String schemaName, String tableName);

    /**
     * 获取指定模式下的所有表名列表。
     *
     * @param schemaName 模式名称
     * @return 表名列表
     */
    List<String> listTables(String schemaName);

    /**
     * 获取指定表的详细信息。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @return 表的详细信息 Map
     */
    Map<String, Object> getTableDetail(String schemaName, String tableName);
}


