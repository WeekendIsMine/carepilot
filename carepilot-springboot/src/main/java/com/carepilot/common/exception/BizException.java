package com.carepilot.common.exception;

import com.carepilot.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：带错误码和中文提示，由全局异常处理器翻译成统一响应，
 * 不会打堆栈（业务错误不是系统故障）。
 */
@Getter
public class BizException extends RuntimeException {
    private final ResultCode resultCode;

    public BizException(String message) {
        super(message);
        this.resultCode = ResultCode.BAD_REQUEST;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }
}
