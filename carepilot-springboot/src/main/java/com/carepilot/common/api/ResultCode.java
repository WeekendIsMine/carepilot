package com.carepilot.common.api;

/**
 * 响应码常量。前两位表示错误大类（40 参数、41 鉴权、50 服务端），便于前端分类处理。
 */
public enum ResultCode {
    OK(0, "ok"),
    BAD_REQUEST(40000, "参数错误"),
    UNAUTHORIZED(40100, "未登录"),
    TOKEN_EXPIRED(40101, "登录已过期"),
    LOGIN_FAILED(40110, "用户名或密码错误"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "状态冲突"),
    RATE_LIMITED(42900, "请求过于频繁"),
    INTERNAL_ERROR(50000, "服务器内部错误"),
    AI_UNAVAILABLE(50300, "AI 服务暂不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() { return code; }
    public String message() { return message; }
}