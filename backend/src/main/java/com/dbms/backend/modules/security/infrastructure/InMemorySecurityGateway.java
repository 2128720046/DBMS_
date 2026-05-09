package com.dbms.backend.modules.security.infrastructure;

import com.dbms.backend.modules.security.domain.SecurityGateway;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 安全模块内存实现。
 * <p>
 * 使用 {@code ConcurrentHashMap} 存储用户和权限信息。
 * 密码使用 SHA-256 哈希存储（MVP 版本）。
 * 默认内置 admin 用户（拥有所有权限），普通用户初始无权限。
 * </p>
 */
@Repository
public class InMemorySecurityGateway implements SecurityGateway {

    /** 用户名 → 密码哈希 */
    private final Map<String, String> users = new ConcurrentHashMap<>();

    /** 用户名 → 授予的权限条目集合（每项格式: "privilege:database.object"） */
    private final Map<String, Set<String>> userPermissions = new ConcurrentHashMap<>();

    /** 所有可用的权限类型 */
    private static final Set<String> ALL_PRIVILEGES = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE",
            "DROP", "ALTER", "INDEX", "BACKUP", "RESTORE"
    );

    public InMemorySecurityGateway() {
        // 内置管理员
        users.put("admin", hashPassword("admin123"));
        // admin 账户特殊标记：所有后续权限检查对 admin 放行
    }

    // ==================== 用户管理 ====================

    @Override
    public String authenticate(String username, String password) {
        String savedHash = users.get(username);
        if (savedHash == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (isAdmin(username)) {
            // admin 使用明文比较兼容旧逻辑
            if (!"admin123".equals(password)) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
        } else {
            if (!savedHash.equals(hashPassword(password))) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
        }
        return Base64.getEncoder().encodeToString((username + ":dbms").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void register(String username, String password) {
        if (users.putIfAbsent(username, hashPassword(password)) != null) {
            throw new IllegalArgumentException("用户已存在");
        }
    }

    @Override
    public void dropUser(String username) {
        if (isAdmin(username)) {
            throw new IllegalArgumentException("不能删除管理员账户");
        }
        if (users.remove(username) == null) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        userPermissions.remove(username);
    }

    @Override
    public void alterUser(String username, String newPassword) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        users.put(username, hashPassword(newPassword));
    }

    // ==================== 权限管理 ====================

    @Override
    public void grant(String username, String privilege, String objectName) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        if (isAdmin(username)) {
            return; // admin 不需要显式授权
        }

        Set<String> perms = userPermissions.computeIfAbsent(username, k -> new CopyOnWriteArraySet<>());

        if ("ALL PRIVILEGES".equalsIgnoreCase(privilege)) {
            for (String p : ALL_PRIVILEGES) {
                perms.add(formatPermission(p, objectName));
            }
        } else {
            perms.add(formatPermission(privilege.toUpperCase(), objectName));
        }
    }

    @Override
    public void revoke(String username, String privilege, String objectName) {
        if (isAdmin(username)) {
            return; // admin 不能撤销自己的权限
        }

        Set<String> perms = userPermissions.get(username);
        if (perms == null) {
            return;
        }

        if ("ALL PRIVILEGES".equalsIgnoreCase(privilege)) {
            for (String p : ALL_PRIVILEGES) {
                perms.remove(formatPermission(p, objectName));
            }
        } else {
            perms.remove(formatPermission(privilege.toUpperCase(), objectName));
        }
    }

    @Override
    public Set<String> permissionsOf(String username, String schemaName, String objectName) {
        if (isAdmin(username)) {
            // admin 拥有所有权限
            Set<String> all = new java.util.LinkedHashSet<>();
            for (String p : ALL_PRIVILEGES) {
                all.add(p + " ON *.*");
            }
            return all;
        }

        Set<String> perms = userPermissions.get(username);
        if (perms == null) {
            return Set.of();
        }

        boolean matchAllSchema = "*".equals(schemaName);
        boolean matchAllObject = "*".equals(objectName);

        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (String entry : perms) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) continue;
            String privilege = parts[0];
            String obj = parts[1];

            if (matchAllSchema && matchAllObject) {
                result.add(privilege + " ON " + obj);
            } else {
                String[] objParts = obj.split("\\.", 2);
                if (objParts.length == 2) {
                    boolean schemaMatch = matchAllSchema || objParts[0].equalsIgnoreCase(schemaName);
                    boolean objMatch = matchAllObject || objParts[1].equalsIgnoreCase(objectName) || "*".equals(objParts[1]);
                    if (schemaMatch && objMatch) {
                        result.add(privilege + " ON " + obj);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void assertAllowed(String username, String schemaName, String objectName, String permission) {
        if (isAdmin(username)) {
            return; // admin 放行所有操作
        }
        Set<String> perms = permissionsOf(username, schemaName, objectName);
        // 检查是否有匹配的权限
        boolean allowed = perms.stream().anyMatch(e -> e.startsWith(permission.toUpperCase() + " ON"));
        if (!allowed) {
            throw new IllegalStateException("用户 '" + username + "' 没有权限: " + permission
                    + " ON " + schemaName + "." + objectName);
        }
    }

    // ==================== 内部工具 ====================

    private boolean isAdmin(String username) {
        return "admin".equals(username);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    private String formatPermission(String privilege, String objectName) {
        return privilege + ":" + objectName;
    }
}
