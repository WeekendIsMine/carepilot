package com.carepilot.user.controller;

import com.carepilot.common.api.Result;
import com.carepilot.security.SecurityUtils;
import com.carepilot.user.dto.AdminUserForm;
import com.carepilot.user.service.UserAdminService;
import com.carepilot.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台用户管理：查询、新建客服、改资料、启停、重置密码。
 */
@Tag(name = "账号管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    /** 用户分页列表；可选角色过滤 */
    @Operation(summary = "账号列表")
    @GetMapping
    public Result<com.carepilot.common.api.PageResult<UserVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(
                userAdminService.list(keyword, role, page, size), UserVO::of));
    }

    /** 新建账号（管理员开客服号走这里） */
    @Operation(summary = "新建账号（客服/管理员只能在这里创建）")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody AdminUserForm form) {
        return Result.ok(userAdminService.create(form));
    }

    /** 修改昵称、角色、最大接待数等 */
    @Operation(summary = "编辑账号")
    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody AdminUserForm form) {
        return Result.ok(userAdminService.update(id, form, SecurityUtils.userId()));
    }

    /** 启用 / 停用；不能停用自己 */
    @Operation(summary = "启用/停用")
    @PostMapping("/{id}/status")
    public Result<UserVO> toggle(@PathVariable Long id) {
        return Result.ok(userAdminService.toggleStatus(id, SecurityUtils.userId()));
    }

    /** 重置密码 */
    @Operation(summary = "重置密码")
    @PostMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userAdminService.resetPassword(id, body.get("password"));
        return Result.ok();
    }
}