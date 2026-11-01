package com.carepilot.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录人：id、用户名、角色。放在 SecurityContext 里供业务代码取用。
 */
@Data
@AllArgsConstructor
public class LoginUser implements Serializable {
    private Long id;
    private String username;
    private String role;
}
