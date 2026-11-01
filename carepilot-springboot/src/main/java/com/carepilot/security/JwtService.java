package com.carepilot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 令牌签发与解析：access 和 refresh 用同一个密钥但带不同 typ，
 * 解析时校验 typ，保证 refresh 不能直接访问业务接口。
 */
@Service
public class JwtService {

    private static final String TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccess(LoginUser user) {
        return issue(user, TYPE_ACCESS, props.getAccessTtl());
    }

    public String issueRefresh(LoginUser user) {
        return issue(user, TYPE_REFRESH, props.getRefreshTtl());
    }

    private String issue(LoginUser user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim(TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public LoginUser parseAccess(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public LoginUser parseRefresh(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public long accessTtl() {
        return props.getAccessTtl();
    }

    private LoginUser parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String type = claims.get(TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new io.jsonwebtoken.JwtException("令牌类型不匹配: " + type);
        }
        return new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
