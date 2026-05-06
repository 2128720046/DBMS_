package com.dbms.backend.controller;

import com.dbms.backend.application.SqlApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.SqlExecuteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SQL 执行控制器，处理 SQL 语句执行的 HTTP 请求。
 * <p>
 * 提供 SQL 语句执行功能。允许跨域访问前端开发服务器（localhost:5173）。
 * </p>
 *
 * @author DBMS Team
 */
@RestController
@RequestMapping("/api/sql")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "SQL", description = "SQL 执行")
public class SqlController {

    /** SQL 应用服务，处理 SQL 语句执行和语法转换逻辑 */
    private final SqlApplicationService sqlApplicationService;

    /**
     * 构造方法。
     *
     * @param sqlApplicationService SQL 应用服务
     */
    public SqlController(SqlApplicationService sqlApplicationService) {
        this.sqlApplicationService = sqlApplicationService;
    }

    /**
     * 执行 SQL 语句接口。
     * <p>
     * 支持 MySQL 风格语法，直接路由到自研原生二进制存储引擎执行。
     * 返回结果分为"消息型"和"表格型"两种格式。
     * </p>
     *
     * @param request SQL 执行请求，包含数据库名和 SQL 语句
     * @return 执行结果，包含类型、状态、数据和影响行数等信息
     */
    @PostMapping("/execute")
    @Operation(summary = "执行 SQL", description = "执行 SQL 语句并返回结果集或影响行数。")
    public ApiResponse<Map<String, Object>> execute(@RequestBody SqlExecuteRequest request) {
        return ApiResponse.ok("执行成功", sqlApplicationService.execute(request.getDatabaseName(), request.getSql()));
    }
}