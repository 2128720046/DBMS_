package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.QueryCondition;
import com.dbms.backend.model.RecordData;
import java.util.List;
import java.util.Map;

/**
 * 记录领域服务接口。
 * <p>
 * 职责：定义记录增删改查的领域语义，保证记录操作行为一致并可复用。
 * 调用方：记录应用服务、索引联动流程。
 */
public interface RecordService {

	/**
	 * 追加记录。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 记录数据
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> appendRecord(String databaseName, String tableName, RecordData recordData);

	/**
	 * 按条件更新记录集合。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param condition 更新条件
	 * @param updateValues 更新值映射
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> updateRecordSet(
			String databaseName,
			String tableName,
			QueryCondition condition,
			Map<String, Object> updateValues);

	/**
	 * 按条件删除记录集合。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param condition 删除条件
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> deleteRecordSet(String databaseName, String tableName, QueryCondition condition);

	/**
	 * 按条件扫描记录。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param condition 查询条件
	 * @return 统一操作结果，data 中返回记录列表
	 */
	OperationResult<List<RecordData>> scanRecords(String databaseName, String tableName, QueryCondition condition);
}
