package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.TableMeta;
import java.util.List;

/**
 * 表元数据仓储接口。
 * <p>
 * 职责：定义表描述文件的读写协议，提供表元信息持久化能力。
 * 调用方：元数据领域服务。
 */
public interface TableMetaRepository {

	/**
	 * 追加表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableMeta 表元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> appendTableMeta(String databaseName, TableMeta tableMeta);

	/**
	 * 更新表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableMeta 新表元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> updateTableMeta(String databaseName, TableMeta tableMeta);

	/**
	 * 删除表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> removeTableMeta(String databaseName, String tableName);

	/**
	 * 读取单个表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回表元信息
	 */
	OperationResult<TableMeta> readTableMeta(String databaseName, String tableName);

	/**
	 * 读取所有表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @return 统一操作结果，data 中返回表元信息列表
	 */
	OperationResult<List<TableMeta>> readAllTableMeta(String databaseName);
}
