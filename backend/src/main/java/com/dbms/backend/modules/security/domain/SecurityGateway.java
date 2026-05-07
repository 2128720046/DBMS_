package com.dbms.backend.modules.security.domain;

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
     * 查询用户在指定数据库和对象上的权限集合。
     *
     * @param username   用户名
     * @param schemaName 数据库名称
     * @param objectName 表名、索引名或其他对象名
     * @return 权限集合，如 SELECT、INSERT、DDL、ADMIN
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
}
