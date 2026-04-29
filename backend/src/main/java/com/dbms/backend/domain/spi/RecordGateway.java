package com.dbms.backend.domain.spi;

import java.util.List;
import java.util.Map;

/**
 * 记录网关接口，定义数据记录的增、删、改、查操作。
 * <p>
 * 作为领域层的 SPI (Service Provider Interface)，由基础设施层实现具体的存储逻辑。
 * 用于解耦领域层与底层记录存储细节。
 * </p>
 *
 * @author DBMS Team
 */
public interface RecordGateway {

    /**
     * 向指定数据库的表中插入一条记录。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @param values     字段名与值的映射
     * @return 受影响的行数
     */
    int insert(String schemaName, String tableName, Map<String, Object> values);

    /**
     * 查询指定数据库的表中符合条件的记录。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @param filters    筛选条件（字段名与值的映射）
     * @param limit      每页记录数
     * @param offset     偏移量
     * @return 符合条件的记录列表
     */
    List<Map<String, Object>> query(String schemaName, String tableName, Map<String, Object> filters, int limit, int offset);

    /**
     * 更新指定数据库的表中符合条件的记录。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @param filters    筛选条件
     * @param values     待更新的字段与值
     * @return 受影响的行数
     */
    int update(String schemaName, String tableName, Map<String, Object> filters, Map<String, Object> values);

    /**
     * 删除指定数据库的表中符合条件的记录。
     *
     * @param schemaName 模式名称
     * @param tableName  表名
     * @param filters    筛选条件
     * @return 受影响的行数
     */
    int delete(String schemaName, String tableName, Map<String, Object> filters);
}