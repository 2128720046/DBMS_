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

/**
 * 表维护控制器，处理表索引和约束管理的 HTTP 请求。
 * <p>
 * 提供索引的创建、删除、重建和列表查询，以及约束的列表查询、
 * 完整性检查和删除功能。允许跨域访问前端开发服务器（localhost:5173）。
 * </p>
 *
 * @author DBMS Team
 */
@RestController
@RequestMapping("/api/databases/{databaseName}/tables/{tableName}")
@CrossOrigin(origins = "http://localhost:5173")
public class TableMaintenanceController {

    /** 表维护应用服务，处理索引和约束管理逻辑 */
    private final TableMaintenanceApplicationService maintenanceApplicationService;

    /**
     * 构造方法。
     *
     * @param maintenanceApplicationService 表维护应用服务
     */
    public TableMaintenanceController(TableMaintenanceApplicationService maintenanceApplicationService) {
        this.maintenanceApplicationService = maintenanceApplicationService;
    }

    /**
     * 查询指定表的所有索引列表。
     *
     * @param databaseName 数据库名称（路径参数）
     * @param tableName    表名（路径参数）
     * @return 索引列表，包含索引名称、是否唯一和包含的列名
     */
    @GetMapping("/indexes")
    public ApiResponse<List<Map<String, Object>>> listIndexes(@PathVariable String databaseName,
                                                              @PathVariable String tableName) {
        return ApiResponse.ok("查询成功", maintenanceApplicationService.listIndexes(databaseName, tableName));
    }

    /**
     * 创建索引。
     *
     * @param databaseName 数据库名称（路径参数）
     * @param tableName    表名（路径参数）
     * @param request      创建索引请求，包含索引名、是否唯一和列列表
     * @return 创建成功的响应
     */
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

    /**
     * 删除索引。
     *
     * @param databaseName 数据库名称（路径参数）
     * @param indexName    索引名（路径参数）
     * @return 删除成功的响应
     */
    @DeleteMapping("/indexes/{indexName}")
    public ApiResponse<Void> dropIndex(@PathVariable String databaseName,
                                       @PathVariable String indexName) {
        maintenanceApplicationService.dropIndex(databaseName, indexName);
        return ApiResponse.ok("索引删除成功", null);
    }

    /**
     * 重建索引（先删除再创建）。
     *
     * @param databaseName 数据库名称（路径参数）
     * @param tableName    表名（路径参数）
     * @param indexName    索引名（路径参数）
     * @return 重建成功的响应
     */
    @PostMapping("/indexes/{indexName}/rebuild")
    public ApiResponse<Void> rebuildIndex(@PathVariable String databaseName,
                                          @PathVariable String tableName,
                                          @PathVariable String indexName) {
        maintenanceApplicationService.rebuildIndex(databaseName, tableName, indexName);
        return ApiResponse.ok("索引重建成功", null);
    }

    /**
     * 查询指定表的所有约束列表。
     * <p>
     * 包括主键约束、唯一约束、外键约束和 NOT NULL 约束。
     * </p>
     *
     * @param databaseName 数据库名称（路径参数）
     * @param tableName    表名（路径参数）
     * @return 约束列表，包含名称、类型、列名（外键还包含引用信息）
     */
    @GetMapping("/constraints")
    public ApiResponse<List<Map<String, Object>>> listConstraints(@PathVariable String databaseName,
                                                                  @PathVariable String tableName) {
        return ApiResponse.ok("查询成功", maintenanceApplicationService.listConstraints(databaseName, tableName));
    }

    /**
     * 检查表的约束完整性和数据一致性。
     * <p>
     * 对于 NOT NULL 约束，检查是否存在空值；对于唯一约束和主键约束，检查是否存在重复数据。
     * </p>
     *
     * @param databaseName 数据库名称（路径参数）
     * @param tableName    表名（路径参数）
     * @return 检查结果，包含是否通过、问题列表和检查项数量
     */
    @PostMapping("/constraints/check")
    public ApiResponse<Map<String, Object>> checkConstraints(@PathVariable String databaseName,
                                                             @PathVariable String tableName) {
        return ApiResponse.ok("检查完成", maintenanceApplicationService.checkConstraints(databaseName, tableName));
    }

    /**
     * 删除约束。
     * <p>
     * 支持删除主键约束、NOT NULL 约束和其他类型的约束。
     * </p>
     *
     * @param databaseName   数据库名称（路径参数）
     * @param tableName      表名（路径参数）
     * @param constraintName 约束名称（路径参数）
     * @return 删除成功的响应
     */
    @DeleteMapping("/constraints/{constraintName}")
    public ApiResponse<Void> dropConstraint(@PathVariable String databaseName,
                                            @PathVariable String tableName,
                                            @PathVariable String constraintName) {
        maintenanceApplicationService.dropConstraint(databaseName, tableName, constraintName);
        return ApiResponse.ok("约束删除成功", null);
    }
}