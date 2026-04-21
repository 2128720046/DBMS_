package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.ConstraintDefinition;
import com.dbms.backend.model.DatabaseMeta;
import com.dbms.backend.model.FieldDefinition;
import com.dbms.backend.model.TableMeta;
import java.util.List;

/**
 * 元数据领域服务接口。
 * <p>
 * 职责：定义数据库、表、字段、约束等结构数据的领域规则与读写语义。
 * 调用方：应用层元数据流程。
 * 使用方式：应用层调用该接口时应保证业务上下文完整，具体存储由仓储层实现。
 */
public interface MetadataService {

	/**
	 * 保存数据库元信息。
	 *
	 * @param databaseMeta 数据库元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> saveDatabaseMeta(DatabaseMeta databaseMeta);

	/**
	 * 读取数据库元信息。
	 *
	 * @param databaseName 数据库名称
	 * @return 统一操作结果，data 中返回数据库元信息
	 */
	OperationResult<DatabaseMeta> loadDatabaseMeta(String databaseName);

	/**
	 * 保存表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableMeta 表元信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> saveTableMeta(String databaseName, TableMeta tableMeta);

	/**
	 * 读取表元信息。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回表元信息
	 */
	OperationResult<TableMeta> loadTableMeta(String databaseName, String tableName);

	/**
	 * 保存字段定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param fields 字段定义列表
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> saveFieldDefinitions(String databaseName, String tableName, List<FieldDefinition> fields);

	/**
	 * 读取字段定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回字段定义列表
	 */
	OperationResult<List<FieldDefinition>> loadFieldDefinitions(String databaseName, String tableName);

	/**
	 * 保存约束定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param constraints 约束定义列表
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> saveConstraints(String databaseName, String tableName, List<ConstraintDefinition> constraints);

	/**
	 * 读取约束定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回约束定义列表
	 */
	OperationResult<List<ConstraintDefinition>> loadConstraints(String databaseName, String tableName);
}
