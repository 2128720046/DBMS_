package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.DatabaseMeta;
import com.dbms.backend.model.UserSession;
import java.util.List;

/**
 * 数据库应用服务接口。
 * <p>
 * 职责：面向控制器层提供数据库级用例入口，统一编排数据库创建、删除、查询与打开流程。
 * 调用方：通常由 REST Controller 调用，也可由客户端通信层间接调用。
 * 使用方式：调用方传入请求参数后，依据返回的 success/message/data 判断执行结果。
 * 返回约定：success=true 表示流程执行成功，message 为结果说明，data 为业务返回值。
 */
public interface DatabaseApplicationService {

	/**
	 * 创建数据库。
	 * <p>
	 * 使用方式：传入数据库元信息后触发校验、目录准备、元数据持久化等流程。
	 *
	 * @param databaseMeta 数据库元信息，包含名称、类型、路径与创建时间等字段
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> createDatabase(DatabaseMeta databaseMeta);

	/**
	 * 删除数据库。
	 * <p>
	 * 使用方式：按数据库名称执行删除流程，必要时可依据 force 参数执行强制删除策略。
	 *
	 * @param databaseName 目标数据库名称
	 * @param force 是否强制删除
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> dropDatabase(String databaseName, boolean force);

	/**
	 * 查询数据库列表。
	 *
	 * @return 统一操作结果，data 中返回数据库元信息列表
	 */
	OperationResult<List<DatabaseMeta>> listDatabases();

	/**
	 * 查询单个数据库详情。
	 *
	 * @param databaseName 目标数据库名称
	 * @return 统一操作结果，data 中返回目标数据库元信息
	 */
	OperationResult<DatabaseMeta> getDatabaseInfo(String databaseName);

	/**
	 * 打开数据库并绑定会话上下文。
	 *
	 * @param databaseName 目标数据库名称
	 * @param session 当前用户会话对象
	 * @return 统一操作结果，data 中返回更新后的会话信息
	 */
	OperationResult<UserSession> openDatabase(String databaseName, UserSession session);
}
