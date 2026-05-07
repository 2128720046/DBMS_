package com.dbms.backend.controller;

import com.dbms.backend.application.TableMaintenanceApplicationService;
import com.dbms.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases/{databaseName}/tables/{tableName}")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class TableMaintenanceController {

    private final TableMaintenanceApplicationService maintenanceApplicationService;

    public TableMaintenanceController(TableMaintenanceApplicationService maintenanceApplicationService) {
        this.maintenanceApplicationService = maintenanceApplicationService;
    }

    @GetMapping("/indexes")
    public ApiResponse<List<Map<String, Object>>> listIndexes(@PathVariable String databaseName,
                                                              @PathVariable String tableName) {
        return ApiResponse.ok("查询成功", maintenanceApplicationService.listIndexes(databaseName, tableName));
    }

    @PostMapping("/indexes")
    public ApiResponse<Void> createIndex(@PathVariable String databaseName,
                                         @PathVariable String tableName,
                                         @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) request.get("columns");
        maintenanceApplicationService.createIndex(databaseName, tableName,
                String.valueOf(request.get("name")), Boolean.TRUE.equals(request.get("unique")), columns);
        return ApiResponse.ok("索引创建成功", null);
    }

    @DeleteMapping("/indexes/{indexName}")
    public ApiResponse<Void> dropIndex(@PathVariable String databaseName,
                                       @PathVariable String indexName) {
        maintenanceApplicationService.dropIndex(databaseName, indexName);
        return ApiResponse.ok("索引删除成功", null);
    }

    @PostMapping("/indexes/{indexName}/rebuild")
    public ApiResponse<Void> rebuildIndex(@PathVariable String databaseName,
                                          @PathVariable String tableName,
                                          @PathVariable String indexName) {
        maintenanceApplicationService.rebuildIndex(databaseName, tableName, indexName);
        return ApiResponse.ok("索引重建成功", null);
    }

    @GetMapping("/constraints")
    public ApiResponse<List<Map<String, Object>>> listConstraints(@PathVariable String databaseName,
                                                                  @PathVariable String tableName) {
        return ApiResponse.ok("查询成功", maintenanceApplicationService.listConstraints(databaseName, tableName));
    }

    @PostMapping("/constraints/check")
    public ApiResponse<Map<String, Object>> checkConstraints(@PathVariable String databaseName,
                                                             @PathVariable String tableName) {
        return ApiResponse.ok("检查完成", maintenanceApplicationService.checkConstraints(databaseName, tableName));
    }

    @DeleteMapping("/constraints/{constraintName}")
    public ApiResponse<Void> dropConstraint(@PathVariable String databaseName,
                                            @PathVariable String tableName,
                                            @PathVariable String constraintName) {
        maintenanceApplicationService.dropConstraint(databaseName, tableName, constraintName);
        return ApiResponse.ok("约束删除成功", null);
    }
}