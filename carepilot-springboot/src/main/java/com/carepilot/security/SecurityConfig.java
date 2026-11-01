package com.carepilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.carepilot.common.api.Result;
import com.carepilot.common.api.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：无状态会话、放行登录/注册/健康检查，其余接口按角色限制，
 * 并注册 JWT 过滤器与统一异常处理。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/ws/**", "/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/workbench/**").hasAnyRole("AGENT", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e

                        .authenticationEntryPoint((req, resp, ex) -> writeJson(resp, 401,
                                Result.fail(ResultCode.UNAUTHORIZED)))

                        .accessDeniedHandler((req, resp, ex) -> writeJson(resp, 403,
                                Result.fail(ResultCode.FORBIDDEN, "当前账号无权访问该资源"))))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse resp, int status, Object body) throws java.io.IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(objectMapper.writeValueAsString(body));
    }
}