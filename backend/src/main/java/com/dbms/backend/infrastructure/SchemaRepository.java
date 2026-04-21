package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.ConstraintDefinition;
import com.dbms.backend.model.FieldDefinition;
import java.util.List;

/**
 * 结构仓储接口。
 * <p>
 * 职责：定义字段定义文件与约束文件的读写协议。
 * 调用方：元数据领域服务、完整性领域服务。
 */
public interface SchemaRepository {

	/**
	 * 写入字段定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param fields 字段定义列表
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writeFieldDefinitions(String databaseName, String tableName, List<FieldDefinition> fields);

	/**
	 * 读取字段定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回字段定义列表
	 */
	OperationResult<List<FieldDefinition>> readFieldDefinitions(String databaseName, String tableName);

	/**
	 * 写入约束定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param constraints 约束定义列表
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writeConstraints(String databaseName, String tableName, List<ConstraintDefinition> constraints);

	/**
	 * 读取约束定义列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @return 统一操作结果，data 中返回约束定义列表
	 */
	OperationResult<List<ConstraintDefinition>> readConstraints(String databaseName, String tableName);
}
