package com.dbms.backend.controller;

import com.dbms.backend.application.AuthApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.LoginRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authApplicationService.login(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody LoginRequest request) {
        authApplicationService.register(request.getUsername(), request.getPassword());
        return ApiResponse.ok("注册成功，请登录", null);
    }
}