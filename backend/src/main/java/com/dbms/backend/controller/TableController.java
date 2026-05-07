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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases/{databaseName}/tables")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class TableController {

    private final TableApplicationService tableApplicationService;

    public TableController(TableApplicationService tableApplicationService) {
        this.tableApplicationService = tableApplicationService;
    }

    /**
     * 创建数据表。
     *
     * @param databaseName 路径参数，来源当前选中数据库。
     * @param request 请求体来源前端建表弹窗，包含表名与列定义。
     * @return 统一协议响应。
     */
    @PostMapping
    public ApiResponse<Void> createTable(@PathVariable String databaseName,
                                         @RequestBody CreateTableRequest request) {
        tableApplicationService.createTable(databaseName, request.getTableName(), request.getColumns());
        return ApiResponse.ok("数据表创建成功", null);
    }

    /**
     * 更新表结构，字段管理页提交的列定义将通过该接口替换到 H2。
     */
    @PutMapping("/{tableName}")
    public ApiResponse<Void> updateTableStructure(@PathVariable String databaseName,
                                                  @PathVariable String tableName,
                                                  @RequestBody CreateTableRequest request) {
        tableApplicationService.updateTableStructure(databaseName, tableName, request.getColumns());
        return ApiResponse.ok("表结构更新成功", null);
    }

    /**
     * 查询指定数据库下的表列表。
     *
     * @param databaseName 路径参数。
     * @return 表信息列表。
     */
    @GetMapping
    public ApiResponse<List<TableInfo>> listTables(@PathVariable String databaseName) {
        return ApiResponse.ok("查询成功", tableApplicationService.listTables(databaseName));
    }

    /**
     * 查询数据表详情与字段信息。
     *
     * @param databaseName 路径参数。
     * @param tableName 路径参数。
     * @return 包含 columns/indexes/fks/ddl 的详情结构。
     */
    @GetMapping("/{tableName}")
    public ApiResponse<Map<String, Object>> getTableDetail(@PathVariable String databaseName,
                                                           @PathVariable String tableName) {
        return ApiResponse.ok("查询成功", tableApplicationService.getTableDetail(databaseName, tableName));
    }

    /**
     * 删除指定数据表。
     *
     * @param databaseName 路径参数。
     * @param tableName 路径参数，来源表列表操作按钮。
     * @return 统一协议响应。
     */
    @DeleteMapping("/{tableName}")
    public ApiResponse<Void> dropTable(@PathVariable String databaseName,
                                       @PathVariable String tableName) {
        tableApplicationService.dropTable(databaseName, tableName);
        return ApiResponse.ok("数据表删除成功", null);
    }
}
