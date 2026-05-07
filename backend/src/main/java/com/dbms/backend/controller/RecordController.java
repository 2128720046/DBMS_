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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases/{databaseName}/tables/{tableName}/records")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class RecordController {

    private final RecordApplicationService recordApplicationService;

    public RecordController(RecordApplicationService recordApplicationService) {
        this.recordApplicationService = recordApplicationService;
    }

    /**
     * 条件查询记录。
     *
     * @param databaseName 路径参数，来源前端当前数据库选择。
     * @param tableName 路径参数，来源前端当前表选择。
     * @param request 请求体，兼容 filters/limit/offset 与 page/size/conditions。
     * @return 协议分页结构，包含 total/page/size/list。
     */
    @PostMapping("/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String databaseName,
                                                  @PathVariable String tableName,
                                                  @RequestBody(required = false) QueryRecordRequest request) {
        QueryRecordRequest safeRequest = request == null ? new QueryRecordRequest() : request;
        List<Map<String, Object>> result = recordApplicationService.query(
                databaseName,
                tableName,
                safeRequest.getFilters(),
                safeRequest.getLimit(),
                safeRequest.getOffset());
        int page = safeRequest.getPage() == null ? 1 : Math.max(safeRequest.getPage(), 1);
        int size = safeRequest.getSize() == null ? result.size() : Math.max(safeRequest.getSize(), 1);
        Map<String, Object> payload = new HashMap<>();
        payload.put("total", result.size());
        payload.put("page", page);
        payload.put("size", size);
        payload.put("list", result);
        return ApiResponse.ok("查询成功", payload);
    }

    /**
     * 插入记录（当前实现支持单条或 records 的第一条）。
     *
     * @param databaseName 路径参数。
     * @param tableName 路径参数。
     * @param request 请求体，兼容 values 与 records。
     * @return 协议结构：affectedRows。
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> insert(@PathVariable String databaseName,
                                                   @PathVariable String tableName,
                                                   @RequestBody CreateRecordRequest request) {
        int affected = recordApplicationService.insert(databaseName, tableName, request.getValues());
        return ApiResponse.ok("插入成功", Map.of("affectedRows", affected));
    }

    /**
     * 更新记录。
     *
     * @param databaseName 路径参数。
     * @param tableName 路径参数。
     * @param request 请求体，兼容 filters/values 与 where/updates。
     * @return 协议结构：affectedRows。
     */
    @PutMapping
    public ApiResponse<Map<String, Object>> update(@PathVariable String databaseName,
                                                   @PathVariable String tableName,
                                                   @RequestBody UpdateRecordRequest request) {
        int affected = recordApplicationService.update(databaseName, tableName, request.getFilters(), request.getValues());
        return ApiResponse.ok("更新成功", Map.of("affectedRows", affected));
    }

    /**
     * 删除记录。
     *
     * @param databaseName 路径参数。
     * @param tableName 路径参数。
     * @param request 请求体，兼容 filters 与 ids。
     * @return 协议结构：affectedRows。
     */
    @DeleteMapping
    public ApiResponse<Map<String, Object>> delete(@PathVariable String databaseName,
                                                   @PathVariable String tableName,
                                                   @RequestBody DeleteRecordRequest request) {
        int affected = recordApplicationService.delete(databaseName, tableName, request.getFilters());
        return ApiResponse.ok("删除成功", Map.of("affectedRows", affected));
    }
}
