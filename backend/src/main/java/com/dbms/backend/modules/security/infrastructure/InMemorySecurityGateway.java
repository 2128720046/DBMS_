package com.dbms.backend.modules.security.infrastructure;

import com.dbms.backend.modules.security.domain.SecurityGateway;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全模块占位实现。
 * <p>
 * 当前使用内存用户表，默认 admin/admin123 可登录。正式实现时在本类内改为读写
 * system_users.dat 和权限文件即可。
 * </p>
 */
@Repository
public class InMemorySecurityGateway implements SecurityGateway {

    private final Map<String, String> users = new ConcurrentHashMap<>();

    public InMemorySecurityGateway() {
        users.put("admin", "admin123");
    }

    @Override
    public String authenticate(String username, String password) {
        String saved = users.get(username);
        if (saved == null || !saved.equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return Base64.getEncoder().encodeToString((username + ":dbms").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void register(String username, String password) {
        if (users.putIfAbsent(username, password) != null) {
            throw new IllegalArgumentException("用户已存在");
        }
    }

    @Override
    public Set<String> permissionsOf(String username, String schemaName, String objectName) {
        return Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "INDEX", "BACKUP", "RESTORE");
    }

    @Override
    public void assertAllowed(String username, String schemaName, String objectName, String permission) {
        if (!permissionsOf(username, schemaName, objectName).contains(permission)) {
            throw new IllegalStateException("用户没有权限: " + permission);
        }
    }
}
