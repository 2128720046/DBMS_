package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.IndexMeta;
import com.dbms.backend.model.QueryCondition;
import com.dbms.backend.model.RecordData;
import java.util.List;
import java.util.Map;

/**
 * 索引领域服务接口。
 * <p>
 * 职责：定义索引构建、索引增量维护与索引检索的核心规则。
 * 调用方：索引应用服务和记录写入流程。
 */
public interface IndexService {

    /**
     * 构建索引。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 表名称
     * @param indexMeta 索引定义
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> buildIndex(String databaseName, String tableName, IndexMeta indexMeta);

    /**
     * 插入记录后更新索引。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 表名称
     * @param recordData 插入记录
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> updateIndexOnInsert(String databaseName, String tableName, RecordData recordData);

    /**
     * 更新记录后更新索引。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 表名称
     * @param condition 更新条件
     * @param updateValues 更新值映射
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> updateIndexOnUpdate(
	    String databaseName,
	    String tableName,
	    QueryCondition condition,
	    Map<String, Object> updateValues);

    /**
     * 删除记录后更新索引。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 表名称
     * @param condition 删除条件
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> updateIndexOnDelete(String databaseName, String tableName, QueryCondition condition);

    /**
     * 通过索引定位记录位置。
     *
     * @param databaseName 所属数据库名称
     * @param tableName 表名称
     * @param indexName 索引名称
     * @param condition 查询条件
     * @return 统一操作结果，data 中返回记录物理位置列表
     */
    OperationResult<List<String>> locateRecordPointers(
	    String databaseName,
	    String tableName,
	    String indexName,
	    QueryCondition condition);
}
