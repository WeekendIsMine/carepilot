package com.carepilot.user.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;

    private String refreshToken;

    private Long expiresIn;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
}
