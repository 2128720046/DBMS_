package com.dbms.backend.modules.security.infrastructure;

import com.dbms.backend.core.storage.config.StorageEngineConfig;
import com.dbms.backend.modules.security.domain.SecurityGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 安全模块内存实现（带文件持久化）。
 * <p>
 * 使用 {@code ConcurrentHashMap} 存储用户和权限信息。
 * 密码使用 SHA-256 哈希存储（MVP 版本）。
 * 默认内置 admin 用户（拥有所有权限），普通用户初始无权限。
 * </p>
 * <p>
 * 用户和权限数据持久化在 {@code {dataDir}/system_users.dat} 中，
 * 每次变更后自动落盘，重启时自动恢复。
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

    /** 用户数据持久化文件路径（功能需求 3.11 要求使用独立的用户文件） */
    private static final String USERS_FILE_NAME = "system_users.dat";

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private String getUsersFilePath() {
        return StorageEngineConfig.getDATA_DIR() + File.separator + USERS_FILE_NAME;
    }

    public InMemorySecurityGateway() {
        // 内置管理员
        users.put("admin", hashPassword("admin123"));
        // admin 账户特殊标记：所有后续权限检查对 admin 放行
    }

    /**
     * 启动时从文件恢复用户和权限数据。
     */
    @PostConstruct
    public void init() {
        StorageEngineConfig.initializeRoot();
        File file = new File(getUsersFilePath());
        if (!file.exists()) {
            // 首次启动：将默认 admin 用户写入文件
            saveToFile();
            return;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});
            // 恢复用户
            Object usersRaw = data.get("users");
            if (usersRaw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> loadedUsers = (Map<String, String>) usersRaw;
                users.clear();
                users.putAll(loadedUsers);
            }
            // 恢复权限
            Object permsRaw = data.get("permissions");
            if (permsRaw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> loadedPerms = (Map<String, List<String>>) permsRaw;
                userPermissions.clear();
                for (Map.Entry<String, List<String>> entry : loadedPerms.entrySet()) {
                    userPermissions.put(entry.getKey(), new CopyOnWriteArraySet<>(entry.getValue()));
                }
            }
        } catch (Exception e) {
            System.err.println("[Security] 加载用户数据文件失败，使用默认配置: " + e.getMessage());
            // 文件损坏/为空时，重置并重新创建
            users.clear();
            userPermissions.clear();
        }
        // 始终确保 admin 用户存在
        if (users.putIfAbsent("admin", hashPassword("admin123")) == null) {
            saveToFile(); // 首次添加 admin 时落盘，修复空文件不创建 admin 的 Bug
        }
    }

    /**
     * 关闭时将当前内存数据持久化到文件。
     */
    @PreDestroy
    public void saveOnDestroy() {
        saveToFile();
    }

    /**
     * 将当前用户和权限数据写入持久化文件。
     */
    private void saveToFile() {
        try {
            StorageEngineConfig.initializeRoot();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users", new LinkedHashMap<>(users));
            data.put("permissions", new LinkedHashMap<>(userPermissions));
            objectMapper.writeValue(new File(getUsersFilePath()), data);
        } catch (Exception e) {
            System.err.println("[Security] 持久化用户数据失败: " + e.getMessage());
        }
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
        saveToFile();
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
        saveToFile();
    }

    @Override
    public void alterUser(String username, String newPassword) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        users.put(username, hashPassword(newPassword));
        saveToFile();
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
            for (String priv : privilege.split(",")) {
                perms.add(formatPermission(priv.trim().toUpperCase(), objectName));
            }
        }
        saveToFile();
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
            for (String priv : privilege.split(",")) {
                perms.remove(formatPermission(priv.trim().toUpperCase(), objectName));
            }
        }
        saveToFile();
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
