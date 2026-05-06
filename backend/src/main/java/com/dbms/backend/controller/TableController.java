package com.dbms.backend.controller;

import com.dbms.backend.application.TableApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.CreateTableRequest;
import com.dbms.backend.model.TableInfo;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases/{databaseName}/tables")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Table", description = "数据表管理")
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
    @Operation(summary = "创建数据表", description = "在指定数据库中创建数据表。")
    public ApiResponse<Void> createTable(@Parameter(description = "数据库名称") @PathVariable String databaseName,
                                         @RequestBody CreateTableRequest request) {
        tableApplicationService.createTable(databaseName, request.getTableName(), request.getColumns());
        return ApiResponse.ok("数据表创建成功", null);
    }

    /**
     * 更新表结构，字段管理页提交的列定义将通过该接口替换到 H2。
     */
    @PutMapping("/{tableName}")
    @Operation(summary = "更新表结构", description = "更新指定表的列定义。")
    public ApiResponse<Void> updateTableStructure(@Parameter(description = "数据库名称") @PathVariable String databaseName,
                                                  @Parameter(description = "数据表名称") @PathVariable String tableName,
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
    @Operation(summary = "查询表列表", description = "查询指定数据库下的全部数据表。")
    public ApiResponse<List<TableInfo>> listTables(@Parameter(description = "数据库名称") @PathVariable String databaseName) {
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
    @Operation(summary = "查询表详情", description = "查询表结构、索引、外键等信息。")
    public ApiResponse<Map<String, Object>> getTableDetail(@Parameter(description = "数据库名称") @PathVariable String databaseName,
                                                           @Parameter(description = "数据表名称") @PathVariable String tableName) {
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
    @Operation(summary = "删除数据表", description = "删除指定数据库中的数据表。")
    public ApiResponse<Void> dropTable(@Parameter(description = "数据库名称") @PathVariable String databaseName,
                                       @Parameter(description = "数据表名称") @PathVariable String tableName) {
        tableApplicationService.dropTable(databaseName, tableName);
        return ApiResponse.ok("数据表删除成功", null);
    }
}
