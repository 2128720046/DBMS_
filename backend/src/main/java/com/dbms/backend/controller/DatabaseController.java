package com.dbms.backend.controller;

import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.CreateDatabaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/databases")
@Validated
public class DatabaseController {

    @GetMapping
    public ApiResponse<List<String>> listDatabases() {
        List<String> mockData = Arrays.asList("demo_db", "course_db");
        return ApiResponse.ok("查询成功", mockData);
    }

    @PostMapping
    public ApiResponse<Void> createDatabase(@Valid @RequestBody CreateDatabaseRequest request) {
        return ApiResponse.ok("数据库创建请求已接收: " + request.getDatabaseName(), null);
    }

    @DeleteMapping("/{databaseName}")
    public ApiResponse<Void> deleteDatabase(@PathVariable @NotBlank String databaseName) {
        return ApiResponse.ok("数据库删除请求已接收: " + databaseName, null);
    }
}

