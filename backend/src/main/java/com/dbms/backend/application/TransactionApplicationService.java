package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.UserSession;

/**
 * 事务应用服务接口。
 * <p>
 * 职责：提供事务开启、提交、回滚和状态查询入口，协调事务与日志模块。
 * 调用方：记录写入流程、批量操作流程。
 */
public interface TransactionApplicationService {

	/**
	 * 开启事务。
	 *
	 * @param session 当前会话信息
	 * @return 统一操作结果，data 中返回事务编号
	 */
	OperationResult<String> beginTransaction(UserSession session);

	/**
	 * 提交事务。
	 *
	 * @param transactionId 事务编号
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> commitTransaction(String transactionId);

	/**
	 * 回滚事务。
	 *
	 * @param transactionId 事务编号
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> rollbackTransaction(String transactionId);

	/**
	 * 查询事务状态。
	 *
	 * @param transactionId 事务编号
	 * @return 统一操作结果，data 中返回事务状态描述
	 */
	OperationResult<String> getTransactionStatus(String transactionId);
}
