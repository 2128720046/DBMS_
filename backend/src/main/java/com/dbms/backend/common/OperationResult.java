package com.dbms.backend.common;

/**
 * 统一操作结果对象?
 * <p>
 * 该类用于封装系统中所有公开接口的返回结果，避免不同模块各自定义返回格式?
 * success 表示操作是否成功，message 用于说明执行结果，data 用于携带业务数据?
 *
 * @param <T> 接口返回的数据类?
 */
public class OperationResult<T> {

    /**
     * 标识当前操作是否成功?
     */
    private boolean success;

    /**
     * 返回给调用方的结果说明信息?
     */
    private String message;

    /**
     * 接口返回的业务数据，可为空?
     */
    private T data;

    public OperationResult() {
    }

    public OperationResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造一个成功结果，并附带自定义提示信息和数据?
     *
     * @param message 成功提示信息
     * @param data 返回数据
     * @param <T> 数据类型
     * @return 成功结果对象
     */
    public static <T> OperationResult<T> success(String message, T data) {
        return new OperationResult<>(true, message, data);
    }

    /**
     * 构造一个成功结果，使用默认成功提示?
     *
     * @param data 返回数据
     * @param <T> 数据类型
     * @return 成功结果对象
     */
    public static <T> OperationResult<T> success(T data) {
        return new OperationResult<>(true, "success", data);
    }

    /**
     * 构造一个失败结果，不附带数据?
     *
     * @param message 失败原因说明
     * @param <T> 数据类型
     * @return 失败结果对象
     */
    public static <T> OperationResult<T> failure(String message) {
        return new OperationResult<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
