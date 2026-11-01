package com.carepilot.agent;

import com.carepilot.chat.ws.FrameSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次 AI 回复的线程上下文：当前用户、会话、输出通道，以及这一轮攒下的引用。
 * 工具方法拿不到 sink 参数，就靠 ThreadLocal 取，回复结束后必须 clear。
 */
public final class AgentContext {

    public record Ctx(Long userId, Long sessionId, FrameSink sink) {}

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<List<Map<String, Object>>> CITATIONS =
            ThreadLocal.withInitial(ArrayList::new);

    private AgentContext() {}

    public static void set(Ctx ctx) {
        HOLDER.set(ctx);
        CITATIONS.set(new ArrayList<>());
    }

    public static Ctx get() { return HOLDER.get(); }

    public static void clear() {
        HOLDER.remove();
        CITATIONS.remove();
    }

    public static Long userId() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.userId();
    }

    public static Long sessionId() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.sessionId();
    }

    public static FrameSink sink() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.sink();
    }

    public static void addCitation(int index, String docTitle, String titlePath, String snippet, double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", index);
        m.put("docTitle", docTitle);
        m.put("titlePath", titlePath);
        m.put("snippet", snippet);
        m.put("score", Math.round(score * 1000) / 1000.0);
        CITATIONS.get().add(m);
    }

    public static List<Map<String, Object>> citations() {
        List<Map<String, Object>> list = CITATIONS.get();
        return list == null ? List.of() : List.copyOf(list);
    }
}