package com.dbms.backend.controller;

import com.dbms.backend.application.RecordApplicationService;
import com.dbms.backend.common.ApiResponse;
import com.dbms.backend.dto.CreateRecordRequest;
import com.dbms.backend.dto.DeleteRecordRequest;
import com.dbms.backend.dto.QueryRecordRequest;
import com.dbms.backend.dto.UpdateRecordRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases/{databaseName}/tables/{tableName}/records")
@CrossOrigin(origins = "http://localhost:5173")
public class RecordController {

    private final RecordApplicationService recordApplicationService;

    public RecordController(RecordApplicationService recordApplicationService) {
        this.recordApplicationService = recordApplicationService;
    }

    @PostMapping("/query")
    public ApiResponse<List<Map<String, Object>>> query(@PathVariable String databaseName,
                                                         @PathVariable String tableName,
                                                         @RequestBody(required = false) QueryRecordRequest request) {
        QueryRecordRequest safeRequest = request == null ? new QueryRecordRequest() : request;
        List<Map<String, Object>> result = recordApplicationService.query(
                databaseName,
                tableName,
                safeRequest.getFilters(),
                safeRequest.getLimit(),
                safeRequest.getOffset());
        return ApiResponse.ok("查询成功", result);
    }

    @PostMapping
    public ApiResponse<Integer> insert(@PathVariable String databaseName,
                                       @PathVariable String tableName,
                                       @RequestBody CreateRecordRequest request) {
        int affected = recordApplicationService.insert(databaseName, tableName, request.getValues());
        return ApiResponse.ok("插入成功", affected);
    }

    @PutMapping
    public ApiResponse<Integer> update(@PathVariable String databaseName,
                                       @PathVariable String tableName,
                                       @RequestBody UpdateRecordRequest request) {
        int affected = recordApplicationService.update(databaseName, tableName, request.getFilters(), request.getValues());
        return ApiResponse.ok("更新成功", affected);
    }

    @DeleteMapping
    public ApiResponse<Integer> delete(@PathVariable String databaseName,
                                       @PathVariable String tableName,
                                       @RequestBody DeleteRecordRequest request) {
        int affected = recordApplicationService.delete(databaseName, tableName, request.getFilters());
        return ApiResponse.ok("删除成功", affected);
    }
}
