package com.dbms.backend.common;

import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验与业务入参错误。
     *
     * @param ex 异常由 Controller 或 Service 参数检查抛出。
     * @return 400 协议错误响应。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 处理数据库层访问异常。
     *
     * @param ex 异常来自 JDBC 执行阶段。
     * @return 500 协议错误响应。
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> handleDataAccessException(DataAccessException ex) {
        return ApiResponse.fail(ErrorCode.DATA_ACCESS_ERROR, "数据库操作失败: " + ex.getMostSpecificCause().getMessage());
    }

    /**
     * 兜底处理所有未捕获异常，避免将异常栈直接暴露给前端。
     *
     * @param ex 其他未处理异常。
     * @return 500 协议错误响应。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "系统内部错误: " + ex.getMessage());
    }
}
