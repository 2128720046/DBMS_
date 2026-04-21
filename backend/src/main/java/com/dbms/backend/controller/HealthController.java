package com.dbms.backend.controller;

import com.dbms.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("service", "dbms-backend");
        payload.put("status", "UP");
        payload.put("time", LocalDateTime.now().toString());
        return ApiResponse.ok("服务运行正常", payload);
    }
}

