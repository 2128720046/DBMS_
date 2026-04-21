package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import java.util.List;

/**
 * 日志仓储接口。
 * <p>
 * 职责：定义事务日志与操作日志的追加、读取和清理协议。
 * 调用方：事务领域服务、备份恢复流程。
 */
public interface LogRepository {

	/**
	 * 追加日志。
	 *
	 * @param databaseName 所属数据库名称
	 * @param transactionId 事务编号
	 * @param logContent 日志内容
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> appendLog(String databaseName, String transactionId, String logContent);

	/**
	 * 读取日志列表。
	 *
	 * @param databaseName 所属数据库名称
	 * @return 统一操作结果，data 中返回日志列表
	 */
	OperationResult<List<String>> readLogs(String databaseName);

	/**
	 * 清理日志。
	 *
	 * @param databaseName 所属数据库名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> clearLogs(String databaseName);
}
