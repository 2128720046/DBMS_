package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.IndexMeta;
import com.dbms.backend.model.QueryCondition;
import com.dbms.backend.model.RecordData;
import java.util.List;

/**
 * 索引应用服务接口。
 * <p>
 * 职责：提供索引创建、删除、重建、查询及索引检索流程。
 * 调用方：索引管理控制器、记录查询流程。
 */
public interface IndexApplicationService {

	/**
	 * 创建索引。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param indexMeta 索引元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> createIndex(String databaseName, String tableName, IndexMeta indexMeta);

	/**
	 * 删除索引。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param indexName 索引名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> dropIndex(String databaseName, String tableName, String indexName);

	/**
	 * 重建索引。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param indexName 索引名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> rebuildIndex(String databaseName, String tableName, String indexName);

	/**
	 * 查询索引列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @return 统一操作结果，data 中返回索引定义列表
	 */
	OperationResult<List<IndexMeta>> listIndexes(String databaseName, String tableName);

	/**
	 * 按索引检索记录。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param indexName 索引名称
	 * @param condition 查询条件
	 * @return 统一操作结果，data 中返回匹配记录列表
	 */
	OperationResult<List<RecordData>> searchByIndex(
			String databaseName,
			String tableName,
			String indexName,
			QueryCondition condition);
}
