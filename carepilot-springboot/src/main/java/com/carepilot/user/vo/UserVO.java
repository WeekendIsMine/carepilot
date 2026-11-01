package com.carepilot.user.vo;

import com.carepilot.user.entity.SysUser;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String phone;
    private Integer status;
    private String agentStatus;
    private Integer maxConcurrent;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public static UserVO of(SysUser u) {
        return UserVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .role(u.getRole())
                .phone(u.getPhone())
                .status(u.getStatus())
                .agentStatus(u.getAgentStatus())
                .maxConcurrent(u.getMaxConcurrent())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
