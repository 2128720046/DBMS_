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

@RestController
@RequestMapping("/api/databases/{databaseName}/backups")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class BackupController {

    private final BackupApplicationService backupApplicationService;

    public BackupController(BackupApplicationService backupApplicationService) {
        this.backupApplicationService = backupApplicationService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listBackups(@PathVariable String databaseName) {
        return ApiResponse.ok("查询成功", backupApplicationService.listBackups(databaseName));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createBackup(@PathVariable String databaseName) {
        return ApiResponse.ok("备份创建成功", backupApplicationService.createBackup(databaseName));
    }

    @PostMapping("/{backupName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String databaseName,
                                           @PathVariable String backupName) {
        backupApplicationService.restoreBackup(databaseName, backupName);
        return ApiResponse.ok("备份恢复成功", null);
    }

    @DeleteMapping("/{backupName}")
    public ApiResponse<Void> deleteBackup(@PathVariable String databaseName,
                                          @PathVariable String backupName) {
        backupApplicationService.deleteBackup(databaseName, backupName);
        return ApiResponse.ok("备份删除成功", null);
    }
}