package com.dbms.backend.modules.database.domain;

import java.util.List;

/**
 * SPI for database schema lifecycle storage operations.
 */
/**
 * 数据库模式网关接口，定义数据库 Schema 的创建、删除和列表查询操作。
 * <p>
 * 作为领域层的 SPI (Service Provider Interface)，由基础设施层实现具体的存储逻辑。
 * 用于解耦领域层与底层数据库存储细节。
 * </p>
 *
 * @author DBMS Team
 */
public interface DatabaseSchemaGateway {

    /**
     * 创建数据库 Schema。
     *
     * @param schemaName 模式名称
     */
    void createSchema(String schemaName);

    /**
     * 删除数据库 Schema。
     *
     * @param schemaName 模式名称
     */
    void dropSchema(String schemaName);

    /**
     * 获取所有数据库 Schema 列表。
     *
     * @return Schema 名称列表
     */
    List<String> listSchemas();
}


