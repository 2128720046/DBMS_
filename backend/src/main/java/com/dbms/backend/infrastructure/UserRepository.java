package com.dbms.backend.infrastructure;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.UserInfo;
import java.util.List;

/**
 * 用户仓储接口。
 * <p>
 * 职责：定义用户信息和权限信息持久化协议。
 * 调用方：安全领域服务。
 */
public interface UserRepository {

	/**
	 * 写入用户信息。
	 *
	 * @param userInfo 用户信息对象
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writeUser(UserInfo userInfo);

	/**
	 * 读取用户信息。
	 *
	 * @param userName 用户名
	 * @return 统一操作结果，data 中返回用户信息
	 */
	OperationResult<UserInfo> readUser(String userName);

	/**
	 * 删除用户信息。
	 *
	 * @param userName 用户名
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> deleteUser(String userName);

	/**
	 * 写入权限列表。
	 *
	 * @param userName 用户名
	 * @param privileges 权限列表
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> writePrivileges(String userName, List<String> privileges);

	/**
	 * 读取权限列表。
	 *
	 * @param userName 用户名
	 * @return 统一操作结果，data 中返回权限列表
	 */
	OperationResult<List<String>> readPrivileges(String userName);
}
