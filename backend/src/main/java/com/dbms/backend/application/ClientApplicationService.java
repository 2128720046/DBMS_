package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import java.util.Map;

/**
 * 客户端通信应用服务接口。
 * <p>
 * 职责：封装客户端连接建立、请求发送、响应接收与断开连接流程。
 * 调用方：远程访问入口或网关控制器。
 */
public interface ClientApplicationService {

	/**
	 * 建立服务端连接并创建会话。
	 *
	 * @param host 服务地址
	 * @param port 服务端口
	 * @param userName 用户名
	 * @param password 密码
	 * @return 统一操作结果，data 中返回连接会话编号
	 */
	OperationResult<String> connectServer(String host, int port, String userName, String password);

	/**
	 * 断开连接。
	 *
	 * @param sessionId 会话编号
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> disconnectServer(String sessionId);

	/**
	 * 发送请求。
	 *
	 * @param sessionId 会话编号
	 * @param requestType 请求类型
	 * @param requestData 请求数据体
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> sendRequest(String sessionId, String requestType, Map<String, Object> requestData);

	/**
	 * 接收响应。
	 *
	 * @param sessionId 会话编号
	 * @return 统一操作结果，data 中返回原始响应内容
	 */
	OperationResult<String> receiveResponse(String sessionId);
}
