package com.carepilot.security;

import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录人工具：从 SecurityContext 取 userId / username / role，
 * 取不到时抛「未登录」而不是返回 null。
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    public static Long userId() {
        return current().getId();
    }

    public static boolean isAgentOrAdmin() {
        String role = current().getRole();
        return "AGENT".equals(role) || "ADMIN".equals(role);
    }
}
