package com.carepilot.chat.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 注册：/ws/chat 走自定义 handler，握手阶段用拦截器校验 JWT。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WsAuthInterceptor wsAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(wsAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
