package com.dbms.backend.modules.security.application;

import com.dbms.backend.modules.security.domain.SecurityGateway;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 用户与权限用例服务。
 */
@Service
public class SecurityApplicationService {

    private final SecurityGateway securityGateway;

    public SecurityApplicationService(SecurityGateway securityGateway) {
        this.securityGateway = securityGateway;
    }

    // ==================== 认证 ====================

    public Map<String, Object> login(String username, String password) {
        String token = securityGateway.authenticate(username, password);
        return Map.of("username", username, "token", token);
    }

    // ==================== 用户管理 ====================

    public void register(String username, String password) {
        securityGateway.register(username, password);
    }

    /** 删除用户（需等待 SqlExecutor 路由就绪后生效） */
    public void dropUser(String username) {
        securityGateway.dropUser(username);
    }

    /** 修改用户密码（需等待 SqlExecutor 路由就绪后生效） */
    public void alterUser(String username, String newPassword) {
        securityGateway.alterUser(username, newPassword);
    }

    // ==================== 权限管理 ====================

    /** 授予权限（需等待 SqlExecutor 路由就绪后生效） */
    public void grant(String username, String privilege, String objectName) {
        securityGateway.grant(username, privilege, objectName);
    }

    /** 撤销权限（需等待 SqlExecutor 路由就绪后生效） */
    public void revoke(String username, String privilege, String objectName) {
        securityGateway.revoke(username, privilege, objectName);
    }

    /** 查询用户权限 */
    public Set<String> permissionsOf(String username, String schemaName, String objectName) {
        return securityGateway.permissionsOf(username, schemaName, objectName);
    }

    /** 断言用户具有指定权限 */
    public void assertAllowed(String username, String schemaName, String objectName, String permission) {
        securityGateway.assertAllowed(username, schemaName, objectName, permission);
    }
}
