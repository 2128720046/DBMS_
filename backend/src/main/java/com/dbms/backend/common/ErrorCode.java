package com.dbms.backend.common;

/**
 * API error codes for SQL-only responses.
 */
public enum ErrorCode {
    /**
     * 请求处理成功。
     */
    SUCCESS(200),
    /**
     * 客户端参数错误，例如字段为空、命名不合法。
     */
    BAD_REQUEST(400),
    /**
     * 数据访问失败，例如 SQL 执行异常。
     */
    DATA_ACCESS_ERROR(500),
    /**
     * 未预期的服务端错误。
     */
    INTERNAL_ERROR(500);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    /**
     * @return 协议中的数值状态码（与 RESTful_API_Protocol.md 对齐）。
     */
    public int getCode() {
        return code;
    }
}


