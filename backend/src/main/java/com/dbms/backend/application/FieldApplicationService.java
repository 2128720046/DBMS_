package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.FieldDefinition;
import java.util.List;

/**
 * 字段应用服务接口。
 * <p>
 * 职责：负责编排字段新增、修改、删除和查询流程，确保结构变更与相关元数据同步。
 * 调用方：表结构管理控制器或高级建模页面。
 */
public interface FieldApplicationService {

	/**
	 * 新增字段。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param fieldDefinition 新增字段定义
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> addField(String databaseName, String tableName, FieldDefinition fieldDefinition);

	/**
	 * 修改字段定义。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param fieldName 目标字段名称
	 * @param newFieldDefinition 新字段定义
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> modifyField(
			String databaseName,
			String tableName,
			String fieldName,
			FieldDefinition newFieldDefinition);

	/**
	 * 删除字段。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @param fieldName 目标字段名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> removeField(String databaseName, String tableName, String fieldName);

	/**
	 * 查询字段列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 目标表名称
	 * @return 统一操作结果，data 中返回字段定义列表
	 */
	OperationResult<List<FieldDefinition>> listFields(String databaseName, String tableName);
}
