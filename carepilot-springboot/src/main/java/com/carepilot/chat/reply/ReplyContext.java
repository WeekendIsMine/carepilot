package com.carepilot.chat.reply;

import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.ws.FrameSink;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 一次回复的输入：会话、用户、用户说的话、历史消息、输出通道。
 */
@Data
@Builder
public class ReplyContext {
    private Long sessionId;
    private Long userId;
    private String userContent;
    private List<ChatMessage> history;
    private FrameSink sink;
}
