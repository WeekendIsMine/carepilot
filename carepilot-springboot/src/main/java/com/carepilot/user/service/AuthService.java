package com.carepilot.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import com.carepilot.security.JwtService;
import com.carepilot.security.LoginUser;
import com.carepilot.user.dto.LoginRequest;
import com.carepilot.user.dto.RegisterRequest;
import com.carepilot.user.entity.SysUser;
import com.carepilot.user.mapper.SysUserMapper;
import com.carepilot.user.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证：登录校验、签发双令牌、刷新令牌换新、注册。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 校验用户名密码和账号状态，签发 access + refresh */
    public LoginVO login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED, ResultCode.LOGIN_FAILED.message());
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        return loginVo(user, jwtService);
    }

    /** 用 refresh 令牌换一对新令牌，旧的不再可用 */
    public LoginVO refresh(String refreshToken) {
        LoginUser parsed;
        try {
            // refresh 令牌只能用来换新令牌：解析时校验 typ=refresh，access 令牌换不了
            parsed = jwtService.parseRefresh(refreshToken);
        } catch (Exception e) {
            throw new BizException(ResultCode.LOGIN_FAILED, "登录已过期，请重新登录");
        }
        SysUser user = userMapper.selectById(parsed.getId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号已失效，请重新登录");
        }
        return loginVo(user, jwtService);
    }

    private LoginVO loginVo(SysUser user, JwtService jwtService) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole());
        return LoginVO.builder()
                .token(jwtService.issueAccess(loginUser))
                .refreshToken(jwtService.issueRefresh(loginUser))
                .expiresIn(jwtService.accessTtl())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    /** 顾客注册，重名直接拒绝 */
    public LoginVO register(RegisterRequest req) {
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername())) > 0) {
            throw BizException.conflict("用户名已被占用");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setPhone(req.getPhone());
        user.setRole("USER");
        user.setStatus(1);
        user.setAgentStatus("OFFLINE");
        user.setMaxConcurrent(5);
        userMapper.insert(user);

        return loginVo(user, jwtService);
    }

    /** 当前登录人信息 */
    public LoginVO me(LoginUser current) {
        SysUser user = userMapper.selectById(current.getId());
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }
}