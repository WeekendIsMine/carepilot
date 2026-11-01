package com.carepilot.knowledge.rewrite;

import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * 查询改写：把口语化问题改写成更适合检索的表达（默认关闭，实测收益不明显但变慢）。
 */
@Slf4j
@Service
public class QueryRewriter {

    private final ChatModel chatModel;
    private final com.carepilot.agent.prompt.PromptRegistry promptRegistry;
    private final String promptTemplate;

    public QueryRewriter(ChatModel chatModel, com.carepilot.agent.prompt.PromptRegistry promptRegistry) {
        this.chatModel = chatModel;
        this.promptRegistry = promptRegistry;
        this.promptTemplate = promptRegistry.content("query-rewrite");
    }

    public String rewrite(String question) {
        try {
            String out = chatModel.call(promptTemplate.formatted(question));
            if (out == null) return question;
            String cleaned = out.replace("\n", " ").trim();
            if (cleaned.isBlank() || cleaned.length() > 120) return question;
            log.debug("query rewritten: {} -> {}", question, cleaned);
            return cleaned;
        } catch (Exception e) {
            log.warn("query rewrite failed, use original: {}", e.getMessage());
            return question;
        }
    }
}