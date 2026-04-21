package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import java.util.Map;

/**
 * 事务领域服务接口。
 * <p>
 * 职责：定义事务日志登记、提交、回滚与系统恢复规则。
 * 调用方：事务应用服务与关键写操作流程。
 */
public interface TransactionService {

	/**
	 * 登记事务操作步骤。
	 *
	 * @param transactionId 事务编号
	 * @param operationType 操作类型
	 * @param operationData 操作数据
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> registerOperation(String transactionId, String operationType, Map<String, Object> operationData);

	/**
	 * 提交事务。
	 *
	 * @param transactionId 事务编号
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> commit(String transactionId);

	/**
	 * 回滚事务。
	 *
	 * @param transactionId 事务编号
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> rollback(String transactionId);

	/**
	 * 恢复未完成事务。
	 *
	 * @param databaseName 目标数据库名称
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> recoverUnfinishedTransactions(String databaseName);
}
