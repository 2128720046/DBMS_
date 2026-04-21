package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.RecordData;

/**
 * 完整性领域服务接口。
 * <p>
 * 职责：定义数据合法性校验规则，覆盖主键、外键、唯一、非空和默认值等约束。
 * 调用方：记录写入流程与结构变更流程。
 */
public interface IntegrityService {

	/**
	 * 校验主键约束。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 待校验记录
	 * @return 统一操作结果，data=true 表示校验通过
	 */
	OperationResult<Boolean> checkPrimaryKey(String databaseName, String tableName, RecordData recordData);

	/**
	 * 校验外键约束。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 待校验记录
	 * @return 统一操作结果，data=true 表示校验通过
	 */
	OperationResult<Boolean> checkForeignKey(String databaseName, String tableName, RecordData recordData);

	/**
	 * 校验非空约束。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 待校验记录
	 * @return 统一操作结果，data=true 表示校验通过
	 */
	OperationResult<Boolean> checkNotNull(String databaseName, String tableName, RecordData recordData);

	/**
	 * 校验唯一约束。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 待校验记录
	 * @return 统一操作结果，data=true 表示校验通过
	 */
	OperationResult<Boolean> checkUnique(String databaseName, String tableName, RecordData recordData);

	/**
	 * 应用默认值规则。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 待处理记录
	 * @return 统一操作结果，data 中返回补全后的记录
	 */
	OperationResult<RecordData> applyDefaultValues(String databaseName, String tableName, RecordData recordData);

	/**
	 * 应用自增规则。
	 *
	 * @param databaseName 所属数据库名称
	 * @param tableName 表名称
	 * @param recordData 待处理记录
	 * @return 统一操作结果，data 中返回处理后的记录
	 */
	OperationResult<RecordData> applyAutoIncrement(String databaseName, String tableName, RecordData recordData);
}
