package com.liveklass.demo.common.response;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(SuccessResponse status, T data) {
        return new ApiResponse<>(true, status.getCode(), status.getMessage(), data);
    }
}
