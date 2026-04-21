package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.ConstraintDefinition;
import com.dbms.backend.model.FieldDefinition;
import com.dbms.backend.model.TableMeta;
import com.dbms.backend.model.TableSchema;
import java.util.List;

/**
 * 表应用服务接口。
 * <p>
 * 职责：提供表级别用例入口，负责建表、改表、删表、查表及表结构展示流程编排。
 * 调用方：数据库管理或表管理控制器。
 * 使用方式：调用方传入数据库名和表结构参数后，按照返回结果驱动页面状态更新。
 */
public interface TableApplicationService {

	/**
	 * 创建表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableMeta 表元信息
	 * @param fields 字段定义列表
	 * @param constraints 约束定义列表
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> createTable(
			String databaseName,
			TableMeta tableMeta,
			List<FieldDefinition> fields,
			List<ConstraintDefinition> constraints);

	/**
	 * 修改表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param newTableMeta 新的表元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> alterTable(String databaseName, String tableName, TableMeta newTableMeta);

	/**
	 * 删除表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> dropTable(String databaseName, String tableName);

	/**
	 * 查询表列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @return 统一操作结果，data 中返回表元信息列表
	 */
	OperationResult<List<TableMeta>> listTables(String databaseName);

	/**
	 * 查询表结构。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @return 统一操作结果，data 中返回完整表结构聚合对象
	 */
	OperationResult<TableSchema> getTableSchema(String databaseName, String tableName);
}
