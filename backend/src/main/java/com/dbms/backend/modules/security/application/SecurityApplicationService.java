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

    public Map<String, Object> login(String username, String password) {
        String token = securityGateway.authenticate(username, password);
        return Map.of("username", username, "token", token);
    }

    public void register(String username, String password) {
        securityGateway.register(username, password);
    }

    public Set<String> permissionsOf(String username, String schemaName, String objectName) {
        return securityGateway.permissionsOf(username, schemaName, objectName);
    }

    public void assertAllowed(String username, String schemaName, String objectName, String permission) {
        securityGateway.assertAllowed(username, schemaName, objectName, permission);
    }
}
