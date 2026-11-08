package com.example.orderai.common.exception;

import lombok.Getter;

/** 业务异常：携带错误码与 HTTP 状态 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BusinessException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(40400, 404, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(40000, 400, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(40100, 401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(40300, 403, message);
    }
}
