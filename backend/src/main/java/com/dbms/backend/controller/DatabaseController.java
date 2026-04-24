package com.dbms.backend.controller;

import com.dbms.backend.application.DatabaseApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.CreateDatabaseRequest;
import com.dbms.backend.model.DatabaseInfo;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/databases")
@CrossOrigin(origins = "http://localhost:5173")
public class DatabaseController {

    private final DatabaseApplicationService applicationService;

    public DatabaseController(DatabaseApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ApiResponse<Void> createDatabase(@RequestBody CreateDatabaseRequest request) {
        applicationService.createDatabase(request.getDbName());
        return ApiResponse.ok("数据库创建成功", null);
    }

    @GetMapping
    public ApiResponse<List<DatabaseInfo>> listDatabases() {
        return ApiResponse.ok("查询成功", applicationService.listDatabases());
    }

    @DeleteMapping("/{databaseName}")
    public ApiResponse<Void> dropDatabase(@PathVariable String databaseName) {
        applicationService.dropDatabase(databaseName);
        return ApiResponse.ok("数据库删除成功", null);
    }
}