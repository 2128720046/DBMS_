package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.IndexMeta;
import java.util.List;
import java.util.Map;

/**
 * 索引仓储接口。
 * <p>
 * 职责：定义索引定义文件和索引数据文件的读写协议。
 * 调用方：索引领域服务。
 */
public interface IndexRepository {

	/**
	 * 写入索引定义。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param indexMeta 索引定义
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writeIndexMeta(String databaseName, String tableName, IndexMeta indexMeta);

	/**
	 * 读取索引定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回索引定义列表
	 */
	OperationResult<List<IndexMeta>> readAllIndexMeta(String databaseName, String tableName);

	/**
	 * 写入索引数据。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param indexName 索引名称
	 * @param entries 索引键到记录位置列表映射
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writeIndexEntries(
			String databaseName,
			String tableName,
			String indexName,
			Map<String, List<String>> entries);

	/**
	 * 读取索引数据。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param indexName 索引名称
	 * @return 统一操作结果，data 中返回索引键到记录位置列表映射
	 */
	OperationResult<Map<String, List<String>>> readIndexEntries(String databaseName, String tableName, String indexName);

	/**
	 * 删除索引数据文件。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param indexName 索引名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> deleteIndexEntries(String databaseName, String tableName, String indexName);
}
