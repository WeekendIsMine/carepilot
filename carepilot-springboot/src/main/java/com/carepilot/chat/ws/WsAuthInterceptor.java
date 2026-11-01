package com.carepilot.chat.ws;

import com.carepilot.security.JwtService;
import com.carepilot.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手拦截：从 URL 的 token 参数解析用户，失败直接拒绝连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            LoginUser user = jwtService.parseAccess(token);
            attributes.put("userId", user.getId());
            attributes.put("username", user.getUsername());
            attributes.put("role", user.getRole());
            return true;
        } catch (Exception e) {
            log.debug("ws handshake rejected: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}