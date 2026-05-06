package com.dbms.backend.controller;

import com.dbms.backend.application.AuthApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.LoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证控制器，处理用户登录和注册的 HTTP 请求。
 * <p>
 * 提供 RESTful API 接口，支持用户登录和注册功能。
 * 允许跨域访问前端开发服务器（localhost:5173）。
 * </p>
 *
 * @author DBMS Team
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Auth", description = "用户认证")
public class AuthController {

    /** 认证应用服务，处理登录和注册业务逻辑 */
    private final AuthApplicationService authApplicationService;

    /**
     * 构造方法。
     *
     * @param authApplicationService 认证应用服务
     */
    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    /**
     * 用户登录接口。
     *
     * @param request 登录请求体，包含用户名和密码
     * @return 包含 token、用户名和角色信息的成功响应
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "提交用户名与密码，返回 token 和用户信息。")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authApplicationService.login(request.getUsername(), request.getPassword()));
    }

    /**
     * 用户注册接口。
     *
     * @param request 注册请求体，包含用户名和密码
     * @return 注册成功的响应
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "提交用户名与密码，完成注册后返回成功提示。")
    public ApiResponse<Void> register(@RequestBody LoginRequest request) {
        authApplicationService.register(request.getUsername(), request.getPassword());
        return ApiResponse.ok("注册成功，请登录", null);
    }
}