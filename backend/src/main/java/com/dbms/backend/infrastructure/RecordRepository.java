package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.QueryCondition;
import com.dbms.backend.model.RecordData;
import java.util.List;
import java.util.Map;

/**
 * 记录仓储接口。
 * <p>
 * 职责：定义记录文件的追加、更新、删除、扫描协议。
 * 调用方：记录领域服务、完整性校验流程。
 */
public interface RecordRepository {

	/**
	 * 追加记录。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 记录数据
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> append(String databaseName, String tableName, RecordData recordData);

	/**
	 * 更新记录集合。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param condition 更新条件
	 * @param updateValues 更新值映射
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> update(String databaseName, String tableName, QueryCondition condition, Map<String, Object> updateValues);

	/**
	 * 删除记录集合。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param condition 删除条件
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> delete(String databaseName, String tableName, QueryCondition condition);

	/**
	 * 扫描记录。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param condition 查询条件
	 * @return 统一操作结果，data 中返回记录列表
	 */
	OperationResult<List<RecordData>> scan(String databaseName, String tableName, QueryCondition condition);
}
