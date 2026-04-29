package com.dbms.backend.controller;

import com.dbms.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

/**
 * 系统控制器，处理系统监控和健康检查的 HTTP 请求。
 * <p>
 * 提供系统健康状态检查功能，返回服务状态、版本信息和运行时间。
 * 允许跨域访问前端开发服务器（localhost:5173）。
 * </p>
 *
 * @author DBMS Team
 */
@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "http://localhost:5173")
public class SystemController {

    /**
     * 系统健康检查接口。
     * <p>
     * 返回系统当前状态、版本信息和运行时间，用于监控系统是否正常运行。
     * </p>
     *
     * @return 包含状态、版本和运行时间的健康检查响应
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        Map<String, Object> payload = Map.of(
                "status", "UP",
                "version", "1.0.0",
                "uptime", uptimeSeconds
        );
        return ApiResponse.ok("success", payload);
    }
}