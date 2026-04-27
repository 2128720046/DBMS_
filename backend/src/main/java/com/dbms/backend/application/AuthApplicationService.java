package com.dbms.backend.application;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuthApplicationService {

    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }
        if (!"admin".equals(username) || !"123456".equals(password)) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        return Map.of(
                "token", "mock-token",
                "username", username,
                "roles", List.of("ADMIN")
        );
    }
}