package com.carepilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构：code / message / data。0 表示成功，非 0 由全局异常处理器产生。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {
    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> ok() { return ok(null); }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.OK.code();
        r.message = ResultCode.OK.message();
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(ResultCode rc) { return fail(rc, rc.message()); }

    public static <T> Result<T> fail(ResultCode rc, String message) {
        Result<T> r = new Result<>();
        r.code = rc.code();
        r.message = message;
        return r;
    }
}
