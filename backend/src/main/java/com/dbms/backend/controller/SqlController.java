package com.dbms.backend.controller;

import com.dbms.backend.application.SqlApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.SqlExecuteRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sql")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class SqlController {

    private final SqlApplicationService sqlApplicationService;

    public SqlController(SqlApplicationService sqlApplicationService) {
        this.sqlApplicationService = sqlApplicationService;
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody SqlExecuteRequest request) {
        return ApiResponse.ok("执行成功", sqlApplicationService.execute(request.getDatabaseName(), request.getSql()));
    }
}
