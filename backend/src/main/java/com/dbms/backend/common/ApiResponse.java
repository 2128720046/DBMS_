package com.dbms.backend.common;

public class ApiResponse<T> {

    /**
     * 协议状态码：200 成功，400 参数错误，500 服务异常。
     */
    private int code;
    /**
     * 响应说明信息，用于前端提示或日志定位。
     */
    private String message;
    /**
     * 业务数据载体。
     */
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构建成功响应。
     *
     * @param message 业务提示语。
     * @param data 业务返回数据。
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 构建失败响应（默认内部错误）。
     *
     * @param message 错误描述。
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(ErrorCode.INTERNAL_ERROR.getCode(), message, null);
    }

    /**
     * 构建带错误码的失败响应。
     *
     * @param code 错误码枚举。
     * @param message 错误描述。
     */
    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(code.getCode(), message, null);
    }

    /**
     * @return 协议状态码。
     */
    public int getCode() {
        return code;
    }

    /**
     * @param code 协议状态码。
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * @return 响应信息。
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message 响应信息。
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return 业务数据。
     */
    public T getData() {
        return data;
    }

    /**
     * @param data 业务数据。
     */
    public void setData(T data) {
        this.data = data;
    }
}