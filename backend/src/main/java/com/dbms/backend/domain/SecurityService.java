package com.dbms.backend.domain;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.UserInfo;
import com.dbms.backend.model.UserSession;
import java.util.List;

/**
 * 安全领域服务接口。
 * <p>
 * 职责：定义用户读取、密码校验、授权判定等安全规则。
 * 调用方：安全应用服务、资源访问流程。
 */
public interface SecurityService {

	/**
	 * 读取用户信息。
	 *
	 * @param userName 用户名
	 * @return 统一操作结果，data 中返回用户信息
	 */
	OperationResult<UserInfo> loadUser(String userName);

	/**
	 * 保存用户信息。
	 *
	 * @param userInfo 用户信息
	 * @return 统一操作结果，成功时不携带额外数据
	 */
	OperationResult<Void> saveUser(UserInfo userInfo);

	/**
	 * 读取权限列表。
	 *
	 * @param userName 用户名
	 * @return 统一操作结果，data 中返回权限列表
	 */
	OperationResult<List<String>> loadPrivileges(String userName);

	/**
	 * 校验密码。
	 *
	 * @param userName 用户名
	 * @param password 密码
	 * @return 统一操作结果，data=true 表示校验通过
	 */
	OperationResult<Boolean> verifyPassword(String userName, String password);

	/**
	 * 权限判定。
	 *
	 * @param session 当前会话
	 * @param action 操作名称
	 * @param resourceName 资源名称
	 * @return 统一操作结果，data=true 表示允许访问
	 */
	OperationResult<Boolean> authorize(UserSession session, String action, String resourceName);
}
