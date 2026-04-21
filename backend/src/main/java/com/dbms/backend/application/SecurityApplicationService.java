package com.dbms.backend.application;

import com.dbms.backend.common.OperationResult;
import com.dbms.backend.model.UserInfo;
import com.dbms.backend.model.UserSession;

/**
 * 安全应用服务接口。
 * <p>
 * 职责：提供登录登出、用户管理、权限授权与校验等安全相关流程入口。
 * 调用方：认证控制器、资源访问控制流程。
 */
public interface SecurityApplicationService {

    /**
     * 用户登录。
     *
     * @param userName 用户名
     * @param password 明文密码或已脱敏密码
     * @return 统一操作结果，data 中返回会话信息
     */
    OperationResult<UserSession> login(String userName, String password);

    /**
     * 用户登出。
     *
     * @param sessionId 会话编号
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> logout(String sessionId);

    /**
     * 创建用户。
     *
     * @param userInfo 用户信息对象
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> createUser(UserInfo userInfo);

    /**
     * 删除用户。
     *
     * @param userName 用户名
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> deleteUser(String userName);

    /**
     * 授权。
     *
     * @param userName 用户名
     * @param databaseName 数据库名称
     * @param resourceName 资源名称
     * @param privilegeType 权限类型
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> grantPrivilege(
	    String userName,
	    String databaseName,
	    String resourceName,
	    String privilegeType);

    /**
     * 撤销授权。
     *
     * @param userName 用户名
     * @param databaseName 数据库名称
     * @param resourceName 资源名称
     * @param privilegeType 权限类型
     * @return 统一操作结果，成功时不携带额外数据
     */
    OperationResult<Void> revokePrivilege(
	    String userName,
	    String databaseName,
	    String resourceName,
	    String privilegeType);

    /**
     * 权限校验。
     *
     * @param session 当前会话
     * @param action 操作名称
     * @param resourceName 资源名称
     * @return 统一操作结果，data=true 表示有权限
     */
    OperationResult<Boolean> checkPermission(UserSession session, String action, String resourceName);
}
