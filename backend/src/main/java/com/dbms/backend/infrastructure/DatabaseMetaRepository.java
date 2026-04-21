package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.DatabaseMeta;
import java.util.List;

/**
 * 数据库元数据仓储接口。
 * <p>
 * 职责：定义数据库描述文件的读写协议，面向上层提供数据库元数据持久化能力。
 * 调用方：元数据领域服务。
 */
public interface DatabaseMetaRepository {

	/**
	 * 追加数据库元信息。
	 *
	 * @param databaseMeta 数据库元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> appendDatabaseMeta(DatabaseMeta databaseMeta);

	/**
	 * 删除数据库元信息。
	 *
	 * @param databaseName 数据库名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> removeDatabaseMeta(String databaseName);

	/**
	 * 读取所有数据库元信息。
	 *
	 * @return 统一操作结果，data 中返回数据库元信息列表
	 */
	OperationResult<List<DatabaseMeta>> readAllDatabaseMeta();

	/**
	 * 读取单个数据库元信息。
	 *
	 * @param databaseName 数据库名称
	 * @return 统一操作结果，data 中返回数据库元信息
	 */
	OperationResult<DatabaseMeta> readDatabaseMeta(String databaseName);
}
