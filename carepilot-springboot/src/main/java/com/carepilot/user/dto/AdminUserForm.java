package com.carepilot.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserForm {
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
    private String username;

    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "角色不能为空")
    private String role;

    @Min(value = 1, message = "最大接待数至少为 1")
    private Integer maxConcurrent = 5;
}
