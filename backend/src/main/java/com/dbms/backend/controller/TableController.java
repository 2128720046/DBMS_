package com.dbms.backend.controller;

import com.dbms.backend.application.TableApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.CreateTableRequest;
import com.dbms.backend.model.TableInfo;
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
@RequestMapping("/api/databases/{databaseName}/tables")
@CrossOrigin(origins = "http://localhost:5173")
public class TableController {

    private final TableApplicationService tableApplicationService;

    public TableController(TableApplicationService tableApplicationService) {
        this.tableApplicationService = tableApplicationService;
    }

    @PostMapping
    public ApiResponse<Void> createTable(@PathVariable String databaseName,
                                         @RequestBody CreateTableRequest request) {
        tableApplicationService.createTable(databaseName, request.getTableName(), request.getColumns());
        return ApiResponse.ok("数据表创建成功", null);
    }

    @GetMapping
    public ApiResponse<List<TableInfo>> listTables(@PathVariable String databaseName) {
        return ApiResponse.ok("查询成功", tableApplicationService.listTables(databaseName));
    }

    @DeleteMapping("/{tableName}")
    public ApiResponse<Void> dropTable(@PathVariable String databaseName,
                                       @PathVariable String tableName) {
        tableApplicationService.dropTable(databaseName, tableName);
        return ApiResponse.ok("数据表删除成功", null);
    }
}
