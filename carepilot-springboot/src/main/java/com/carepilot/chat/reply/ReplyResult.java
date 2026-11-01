package com.carepilot.chat.reply;

import java.util.List;
import java.util.Map;

/**
 * 一次回复的输出：完整答案、引用列表、是否无可奉告。
 */
public record ReplyResult(String text, List<Map<String, Object>> citations) {
    public static ReplyResult of(String text) {
        return new ReplyResult(text, List.of());
    }
}
