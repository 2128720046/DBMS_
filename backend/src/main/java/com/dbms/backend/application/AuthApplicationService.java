package com.dbms.backend.application;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class AuthApplicationService {

    // Simple in-memory user store for registration
    private final Map<String, String> userStore = new HashMap<>();

    public AuthApplicationService() {
        // Pre-register admin
        userStore.put("admin", "123456");
    }

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