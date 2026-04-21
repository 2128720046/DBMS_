package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import java.util.Map;

/**
 * 客户端通信领域服务接口。
 * <p>
 * 职责：定义服务监听、请求解析、请求分发和响应发送规则。
 * 调用方：客户端应用服务或网络接入层。
 */
public interface ClientService {

	/**
	 * 启动服务监听。
	 *
	 * @param port 监听端口
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> startServer(int port);

	/**
	 * 停止服务监听。
	 *
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> stopServer();

	/**
	 * 解析客户端原始请求。
	 *
	 * @param rawRequest 原始请求文本
	 * @return 统一操作结果，data 中返回解析后的请求对象
	 */
	OperationResult<Map<String, Object>> acceptRequest(String rawRequest);

	/**
	 * 分发请求到对应应用服务。
	 *
	 * @param requestType 请求类型
	 * @param requestData 请求数据体
	 * @return 统一操作结果，data 中返回分发执行后的结果对象
	 */
	OperationResult<OperationResult<?>> dispatchRequest(String requestType, Map<String, Object> requestData);

	/**
	 * 向客户端发送响应。
	 *
	 * @param clientId 客户端标识
	 * @param result 统一结果对象
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> sendResponse(String clientId, OperationResult<?> result);
}
