package com.dbms.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录/注册请求")
public class LoginRequest {

    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "密码", example = "123456")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}