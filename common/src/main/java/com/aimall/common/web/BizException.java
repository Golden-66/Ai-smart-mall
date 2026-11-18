package com.aimall.common.web;

public class BizException extends RuntimeException {
    private final int httpStatus;

    public BizException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public BizException(String message) {
        this(400, message);
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
