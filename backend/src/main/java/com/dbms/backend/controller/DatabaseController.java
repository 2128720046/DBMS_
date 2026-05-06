package com.dbms.backend.controller;

import com.dbms.backend.application.DatabaseApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.CreateDatabaseRequest;
import com.dbms.backend.model.DatabaseInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Database", description = "数据库管理")
public class DatabaseController {

    private final DatabaseApplicationService applicationService;

    public DatabaseController(DatabaseApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建数据库。
     *
     * @param request 请求体来自前端“新建数据库”弹窗，字段兼容 name/dbName。
     * @return 统一协议响应。
     */
    @PostMapping
    @Operation(summary = "创建数据库", description = "创建新的数据库实例。")
    public ApiResponse<Void> createDatabase(@RequestBody CreateDatabaseRequest request) {
        applicationService.createDatabase(request.getDbName());
        return ApiResponse.ok("数据库创建成功", null);
    }

    /**
     * 查询数据库列表。
     *
     * @return 数据库名称集合，用于前端数据库下拉与列表展示。
     */
    @GetMapping
    @Operation(summary = "查询数据库列表", description = "查询当前系统中已创建的数据库。")
    public ApiResponse<List<DatabaseInfo>> listDatabases() {
        return ApiResponse.ok("查询成功", applicationService.listDatabases());
    }

    /**
     * 删除数据库。
     *
     * @param databaseName 路径参数，来源前端列表删除按钮。
     * @return 统一协议响应。
     */
    @DeleteMapping("/{databaseName}")
    @Operation(summary = "删除数据库", description = "删除指定名称的数据库。")
    public ApiResponse<Void> dropDatabase(@Parameter(description = "数据库名称") @PathVariable String databaseName) {
        applicationService.dropDatabase(databaseName);
        return ApiResponse.ok("数据库删除成功", null);
    }
}