package com.carepilot.chat.ws;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WS 帧构造：统一拼 {type, ...} 结构，避免各个发送点手写 Map 出现字段不一致。
 */
public final class WsFrames {

    private WsFrames() {}

    public static Map<String, Object> of(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    public static Map<String, Object> of(String type, String key, Object value) {
        Map<String, Object> m = of(type);
        m.put(key, value);
        return m;
    }

    public static Map<String, Object> error(int code, String message) {
        Map<String, Object> m = of(WsFrameType.ERROR);
        m.put("code", code);
        m.put("message", message);
        return m;
    }
}
