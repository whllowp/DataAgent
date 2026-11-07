package com.dataagent.web;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "SQL_NOT_ALLOWED", message);
    }

    public static ApiException accessDenied(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException badGateway(String message, Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", message, cause);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", message);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
