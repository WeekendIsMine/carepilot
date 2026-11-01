package com.carepilot.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import com.carepilot.user.dto.AdminUserForm;
import com.carepilot.user.entity.SysUser;
import com.carepilot.user.mapper.SysUserMapper;
import com.carepilot.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 后台用户管理：查询、建号、改资料、启停、重置密码。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Set<String> ROLES = Set.of("USER", "AGENT", "ADMIN");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页列表，可按角色过滤 */
    public com.baomidou.mybatisplus.core.metadata.IPage<SysUser> list(String keyword, String role, Integer page, Integer size) {
        return userMapper.selectPage(com.carepilot.common.api.Pages.of(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getNickname, keyword))
                        .eq(StringUtils.hasText(role), SysUser::getRole, role)
                        .orderByAsc(SysUser::getId));
    }

    /** 新建账号，密码用 BCrypt 存 */
    @Transactional
    public UserVO create(AdminUserForm form) {
        String role = normalizeRole(form.getRole());
        if (!StringUtils.hasText(form.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新建账号必须设置密码");
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, form.getUsername())) > 0) {
            throw BizException.conflict("用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(form.getUsername());
        u.setPassword(passwordEncoder.encode(form.getPassword()));
        u.setNickname(form.getNickname());
        u.setPhone(form.getPhone());
        u.setRole(role);
        u.setStatus(1);
        u.setAgentStatus("OFFLINE");
        u.setMaxConcurrent(form.getMaxConcurrent() == null ? 5 : form.getMaxConcurrent());
        userMapper.insert(u);
        return UserVO.of(u);
    }

    /** 改昵称/角色/并发接待数 */
    @Transactional
    public UserVO update(Long id, AdminUserForm form, Long operatorId) {
        SysUser u = require(id);
        String role = normalizeRole(form.getRole());
        if (operatorId.equals(id) && !"ADMIN".equals(role)) {
            throw BizException.conflict("不能把自己降级为非管理员");
        }
        if ("ADMIN".equals(u.getRole()) && !"ADMIN".equals(role)
                && userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRole, "ADMIN")) <= 1) {
            throw BizException.conflict("系统至少保留一个管理员");
        }

        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setNickname(form.getNickname());
        patch.setPhone(form.getPhone());
        patch.setRole(role);
        patch.setMaxConcurrent(form.getMaxConcurrent() == null ? u.getMaxConcurrent() : form.getMaxConcurrent());
        userMapper.updateById(patch);

        if (StringUtils.hasText(form.getPassword())) {
            resetPassword(id, form.getPassword());
        }
        return UserVO.of(userMapper.selectById(id));
    }

    /** 启停账号；不允许停用自己 */
    @Transactional
    public UserVO toggleStatus(Long id, Long operatorId) {
        SysUser u = require(id);
        if (operatorId.equals(id)) throw BizException.conflict("不能停用自己的账号");
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setStatus(u.getStatus() != null && u.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(patch);
        return UserVO.of(userMapper.selectById(id));
    }

    /** 重置密码 */
    public void resetPassword(Long id, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 32) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度需为 6-32 位");
        }
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.updateById(patch);
    }

    private SysUser require(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw BizException.notFound("账号不存在");
        return u;
    }

    private String normalizeRole(String role) {
        String r = role == null ? "" : role.trim().toUpperCase();
        if (!ROLES.contains(r)) throw new BizException(ResultCode.BAD_REQUEST, "角色不合法");
        return r;
    }
}