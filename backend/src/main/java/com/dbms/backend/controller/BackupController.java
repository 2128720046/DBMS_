package com.dbms.backend.controller;

import com.dbms.backend.application.BackupApplicationService;
import com.dbms.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 备份控制器，处理数据库备份和恢复的 HTTP 请求。
 * <p>
 * 提供备份的创建、列表查询、恢复和删除功能。
 * 允许跨域访问前端开发服务器（localhost:5173）。
 * </p>
 *
 * @author DBMS Team
 */
@RestController
@RequestMapping("/api/databases/{databaseName}/backups")
@CrossOrigin(origins = "http://localhost:5173")
public class BackupController {

    /** 备份应用服务，处理备份和恢复业务逻辑 */
    private final BackupApplicationService backupApplicationService;

    /**
     * 构造方法。
     *
     * @param backupApplicationService 备份应用服务
     */
    public BackupController(BackupApplicationService backupApplicationService) {
        this.backupApplicationService = backupApplicationService;
    }

    /**
     * 查询指定数据库的所有备份文件列表。
     *
     * @param databaseName 数据库名称（路径参数）
     * @return 备份文件列表，包含名称、大小和更新时间信息
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listBackups(@PathVariable String databaseName) {
        return ApiResponse.ok("查询成功", backupApplicationService.listBackups(databaseName));
    }

    /**
     * 为指定数据库创建备份。
     *
     * @param databaseName 数据库名称（路径参数）
     * @return 包含备份文件名和路径的成功响应
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createBackup(@PathVariable String databaseName) {
        return ApiResponse.ok("备份创建成功", backupApplicationService.createBackup(databaseName));
    }

    /**
     * 恢复指定数据库的备份。
     *
     * @param databaseName 数据库名称（路径参数）
     * @param backupName   备份文件名（路径参数）
     * @return 恢复成功的响应
     */
    @PostMapping("/{backupName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String databaseName,
                                           @PathVariable String backupName) {
        backupApplicationService.restoreBackup(databaseName, backupName);
        return ApiResponse.ok("备份恢复成功", null);
    }

    /**
     * 删除指定数据库的备份文件。
     *
     * @param databaseName 数据库名称（路径参数）
     * @param backupName   备份文件名（路径参数）
     * @return 删除成功的响应
     */
    @DeleteMapping("/{backupName}")
    public ApiResponse<Void> deleteBackup(@PathVariable String databaseName,
                                          @PathVariable String backupName) {
        backupApplicationService.deleteBackup(databaseName, backupName);
        return ApiResponse.ok("备份删除成功", null);
    }
}