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
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
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
    public ApiResponse<Void> dropDatabase(@PathVariable String databaseName) {
        applicationService.dropDatabase(databaseName);
        return ApiResponse.ok("数据库删除成功", null);
    }
}