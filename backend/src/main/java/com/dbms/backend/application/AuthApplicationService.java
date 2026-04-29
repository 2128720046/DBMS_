package com.dbms.backend.application;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * 认证应用服务，负责处理用户登录和注册的业务逻辑。
 * <p>
 * 使用内存方式存储用户信息，提供简单的用户认证功能。
 * 预置了 admin 管理员账号（密码：123456）。
 * </p>
 *
 * @author DBMS Team
 */
@Service
public class AuthApplicationService {

    /** 简单的内存用户存储，用于注册和登录验证 */
    private final Map<String, String> userStore = new HashMap<>();

    /**
     * 构造方法，预注册管理员账号。
     */
    public AuthApplicationService() {
        // Pre-register admin
        userStore.put("admin", "123456");
    }

    /**
     * 用户登录验证。
     *
     * @param username 用户名
     * @param password 密码
     * @return 包含 token、用户名和角色信息的 Map
     * @throws IllegalArgumentException 如果用户名或密码为空，或账号/密码错误
     */
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }
        
        String storedPassword = userStore.get(username);
        if (storedPassword == null || !storedPassword.equals(password)) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        
        return Map.of(
                "token", UUID.randomUUID().toString(),
                "username", username,
                "roles", "admin".equals(username) ? List.of("ADMIN") : List.of("USER")
        );
    }

    /**
     * 用户注册。
     *
     * @param username 用户名
     * @param password 密码
     * @throws IllegalArgumentException 如果用户名或密码为空，或账号已存在
     */
    public void register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }
        if (userStore.containsKey(username)) {
            throw new IllegalArgumentException("账号已存在");
        }
        userStore.put(username, password);
    }
}