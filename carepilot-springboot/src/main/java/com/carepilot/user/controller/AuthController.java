package com.carepilot.user.controller;

import com.carepilot.common.api.Result;

import com.carepilot.common.api.ResultCode;
import com.carepilot.security.SecurityUtils;
import com.carepilot.user.dto.LoginRequest;
import com.carepilot.user.dto.RegisterRequest;
import com.carepilot.user.service.AuthService;
import com.carepilot.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证入口：登录、注册、用刷新令牌换新令牌、取当前登录人。
 * 登录成功返回 access + refresh 双令牌，后续业务接口只认 access。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用刷新令牌换一对新令牌；refresh 令牌不能直接访问业务接口 */
    @Operation(summary = "用刷新令牌换取新的访问令牌")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body == null ? null : body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.fail(ResultCode.BAD_REQUEST, "缺少 refreshToken");
        }
        return Result.ok(authService.refresh(refreshToken));
    }

    /** 用户名密码登录，校验通过后签发双令牌并返回用户信息 */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /** 顾客自助注册，注册完直接返回令牌，前端可以免二次登录 */
    @Operation(summary = "顾客注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    /** 取当前登录人信息，前端刷新页面时用它恢复状态 */
    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.ok(authService.me(SecurityUtils.current()));
    }
}