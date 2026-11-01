package com.carepilot.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置：密钥、签发者、access / refresh 有效期。
 */
@Data
@Component
@ConfigurationProperties(prefix = "carepilot.jwt")
public class JwtProperties {

    private String secret = "carepilot-dev-secret-key-change-me-0123456789";

    private long accessTtl = 7200;

    private long refreshTtl = 2592000;
    private String issuer = "carepilot";
}