package com.aimall.common.web;

import com.aimall.common.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 各 Servlet 服务通过 @Import 引入。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> biz(BizException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(Result.fail(e.getHttpStatus(), e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> header(MissingRequestHeaderException e) {
        return ResponseEntity.status(401).body(Result.fail(401, "未登录"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> other(Exception e) {
        log.error("unhandled error", e);
        return ResponseEntity.status(500).body(Result.fail(500, "服务内部错误: " + e.getMessage()));
    }
}
