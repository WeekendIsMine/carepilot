package com.carepilot.common.exception;

import com.carepilot.common.api.Result;
import com.carepilot.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把各类异常翻译成统一响应与中文提示，避免前端看到 500 和英文堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("biz error: {}", e.getMessage());
        return Result.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数错误");
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public Result<Void> handleBadParam(Exception e) {
        log.warn("bad param: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "参数格式不正确");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("unhandled error", e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}