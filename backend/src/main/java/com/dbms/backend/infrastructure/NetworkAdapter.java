package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;

/**
 * 网络适配器接口。
 * <p>
 * 职责：定义网络连接建立、关闭、读取与写回协议。
 * 调用方：客户端通信领域服务。
 */
public interface NetworkAdapter {

	/**
	 * 打开网络连接。
	 *
	 * @param host 目标主机
	 * @param port 目标端口
	 * @return 统一操作结果，data 中返回连接编号
	 */
	OperationResult<String> openConnection(String host, int port);

	/**
	 * 关闭网络连接。
	 *
	 * @param connectionId 连接编号
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> closeConnection(String connectionId);

	/**
	 * 读取请求或响应内容。
	 *
	 * @param connectionId 连接编号
	 * @return 统一操作结果，data 中返回读取到的报文字符串
	 */
	OperationResult<String> readRequest(String connectionId);

	/**
	 * 写回响应内容。
	 *
	 * @param connectionId 连接编号
	 * @param responseBody 响应内容
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writeResponse(String connectionId, String responseBody);
}
