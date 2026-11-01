package com.carepilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 过滤器：从 Authorization 头解析 access 令牌，把用户放进 SecurityContext，
 * 令牌无效或过期就放行到匿名（由后续鉴权规则拒绝）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                LoginUser user = jwtService.parseAccess(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                log.debug("invalid token: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}