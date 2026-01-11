package com.crypto.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一API响应对象
 * 用于封装所有API接口的返回数据
 *
 * @param <T> 响应数据类型
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码
     * 200: 成功
     * 400: 客户端错误
     * 500: 服务器错误
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 请求追踪ID
     */
    private String traceId;

    /**
     * 成功响应(无数据)
     *
     * @param <T> 数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Success")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 成功响应(带数据)
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 成功响应(带自定义消息和数据)
     *
     * @param message 响应消息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 失败响应
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .code(500)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 失败响应(带状态码)
     *
     * @param code    状态码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 参数错误响应
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        return ApiResponse.<T>builder()
                .code(400)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 未授权响应
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return ApiResponse.<T>builder()
                .code(401)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 资源未找到响应
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return ApiResponse.<T>builder()
                .code(404)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 判断响应是否成功
     *
     * @return true-成功, false-失败
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
}
