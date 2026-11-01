package com.carepilot.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 会话消息读写。用户消息按 clientMsgId 做幂等，重发不会产生重复记录。
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper messageMapper;
    private final com.carepilot.agent.prompt.PromptRegistry promptRegistry;

    /** 追加一条消息，返回落库后的实体 */
    public ChatMessage append(Long sessionId, String role, String content,
                              String clientMsgId, String toolCalls, String citations) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setClientMsgId(clientMsgId);
        m.setToolCalls(toolCalls);
        m.setCitations(citations);
        if ("AI".equals(role)) {

            m.setPromptVersion(promptRegistry.version("aftersale-agent"));
        }
        try {
            messageMapper.insert(m);
            return m;
        } catch (DuplicateKeyException e) {
            return messageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .eq(ChatMessage::getClientMsgId, clientMsgId));
        }
    }

    /** 按客户端的幂等键找已有用户消息 */
    public ChatMessage findUserMessage(Long sessionId, String clientMsgId) {
        return messageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getClientMsgId, clientMsgId));
    }

    /** 删除会话时连带清理消息 */
    public void deleteBySession(Long sessionId) {
        messageMapper.delete(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, sessionId));
    }

    /** 最近 N 条历史，给大模型做上下文 */
    public List<ChatMessage> history(Long sessionId, int limit) {
        List<ChatMessage> list = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("limit " + Math.min(limit, 200)));
        Collections.reverse(list);
        return list;
    }
}