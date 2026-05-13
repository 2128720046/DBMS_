package com.dbms.backend.modules.security.domain;

import java.util.List;
import java.util.Set;

/**
 * 安全性模块网关。
 * <p>
 * 该接口对应验收要求 3.11，负责用户、角色和权限检查。其他模块只发起权限判断，
 * 不直接读取用户文件或持久化格式。
 * </p>
 */
public interface SecurityGateway {

    /**
     * 校验用户凭据并返回访问令牌。
     *
     * @param username 用户名
     * @param password 明文或前端约定格式的密码
     * @return 访问令牌
     */
    String authenticate(String username, String password);

    /**
     * 注册新用户。
     *
     * @param username 用户名
     * @param password 密码
     */
    void register(String username, String password);

    /**
     * 删除用户。
     *
     * @param username 要删除的用户名
     */
    void dropUser(String username);

    /**
     * 修改用户密码。
     *
     * @param username   用户名
     * @param newPassword 新密码
     */
    void alterUser(String username, String newPassword);

    /**
     * 授予用户对指定对象的权限。
     *
     * @param username   用户名
     * @param privilege  权限类型（SELECT、INSERT、UPDATE、DELETE、CREATE、DROP、ALTER、INDEX、BACKUP、RESTORE、ALL PRIVILEGES）
     * @param objectName 权限作用对象，格式为 database.table 或 database.*
     */
    void grant(String username, String privilege, String objectName);

    /**
     * 撤销用户对指定对象的权限。
     *
     * @param username   用户名
     * @param privilege  权限类型
     * @param objectName 权限作用对象
     */
    void revoke(String username, String privilege, String objectName);

    /**
     * 查询用户在指定数据库和对象上的权限集合。
     *
     * @param username   用户名
     * @param schemaName 数据库名称，使用 "*" 表示所有库
     * @param objectName 表名、索引名或其他对象名，使用 "*" 表示所有对象
     * @return 权限集合，条目格式为 "privilege_type ON object_name"
     */
    Set<String> permissionsOf(String username, String schemaName, String objectName);

    /**
     * 断言用户具有指定权限；没有权限时由实现抛出异常。
     *
     * @param username   用户名
     * @param schemaName 数据库名称
     * @param objectName 表名、索引名或其他对象名
     * @param permission 需要的权限
     */
    void assertAllowed(String username, String schemaName, String objectName, String permission);

    /**
     * 根据令牌解析用户名。
     *
     * @param token 登录时返回的访问令牌
     * @return 用户名；令牌无效时返回 null
     */
    String resolveByToken(String token);

    /**
     * 列出所有已注册用户。
     *
     * @return 用户名列表
     */
    List<String> listAllUsers();

    /**
     * 使指定用户的所有令牌失效（用于强制下线）。
     *
     * @param username 用户名
     */
    void invalidateTokensByUsername(String username);
}
