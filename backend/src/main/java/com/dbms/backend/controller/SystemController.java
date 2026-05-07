package com.dbms.backend.controller;

import com.dbms.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class SystemController {

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
