package com.carepilot.chat.ws;

import java.util.Map;

/**
 * 帧输出口。把「往哪个连接发什么帧」抽象出来，业务代码不直接碰 WebSocketSession。
 */
@FunctionalInterface
public interface FrameSink {
    void send(Map<String, Object> frame);

    default void send(String type, String key, Object value) {
        send(WsFrames.of(type, key, value));
    }
}
