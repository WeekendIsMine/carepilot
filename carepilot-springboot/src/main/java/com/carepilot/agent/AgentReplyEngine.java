package com.carepilot.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.carepilot.agent.tools.OrderTools;
import com.carepilot.agent.tools.PolicyTools;
import com.carepilot.agent.tools.TicketTools;
import com.carepilot.agent.tools.TransferTools;
import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.reply.ReplyContext;
import com.carepilot.chat.reply.ReplyEngine;
import com.carepilot.chat.reply.ReplyResult;
import com.carepilot.chat.ws.FrameSink;
import com.carepilot.chat.ws.WsFrameType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * AI 回复主引擎：用 ReactAgent 跑「模型 → 调工具 → 再推理」的多轮循环，
 * 把增量文本通过 WebSocket 推给前端，最后返回完整答案与引用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carepilot.reply.engine", havingValue = "agent", matchIfMissing = true)
public class AgentReplyEngine implements ReplyEngine {

    private final ChatModel chatModel;
    private final OrderTools orderTools;
    private final TicketTools ticketTools;
    private final PolicyTools policyTools;
    private final TransferTools transferTools;
    private final AgentProperties properties;
    private final com.carepilot.agent.prompt.PromptRegistry promptRegistry;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private String instruction;

    @PostConstruct
    void loadPrompt() {
        var prompt = promptRegistry.get("aftersale-agent");
        this.instruction = prompt.content();
        log.info("agent prompt loaded: version={} updated={}", prompt.version(), prompt.updated());
    }

    @Override
    public ReplyResult reply(ReplyContext ctx) throws Exception {
        AgentContext.set(new AgentContext.Ctx(ctx.getUserId(), ctx.getSessionId(), ctx.getSink()));

        ReactAgent agent = ReactAgent.builder()
                .name("carepilot_aftersale")
                .model(chatModel)
                .instruction(instruction)
                .methodTools(orderTools, ticketTools, policyTools, transferTools)
                .build();

        String input = buildInput(ctx);
        log.debug("agent input: {}", input);
        long start = System.currentTimeMillis();
        io.micrometer.core.instrument.Timer.Sample sample =
                io.micrometer.core.instrument.Timer.start(meterRegistry);

        var answer = agent.call(input, RunnableConfig.builder()
                .threadId("s-" + ctx.getSessionId())
                .build());

        String text = answer == null || answer.getText() == null ? "" : answer.getText();
        sample.stop(meterRegistry.timer("carepilot.ai.reply", "engine", "agent"));

        if (com.carepilot.chat.reply.LlmErrors.looksLikeError(text)) {
            log.warn("agent returned error text (session={}): {}", ctx.getSessionId(),
                    com.carepilot.chat.reply.LlmErrors.abbreviate(text));
            text = com.carepilot.chat.reply.LlmErrors.friendly(text);
            emitChunks(ctx.getSink(), text);
            return new ReplyResult(text, java.util.List.of());
        }
        log.info("agent answered in {} ms, {} chars", System.currentTimeMillis() - start, text.length());

        emitChunks(ctx.getSink(), text);
        return new ReplyResult(text, AgentContext.citations());
    }

    private void emitChunks(FrameSink sink, String text) {
        if (text == null || text.isEmpty()) return;
        int size = Math.max(4, properties.getChunkSize());
        for (int i = 0; i < text.length(); i += size) {
            sink.send(WsFrameType.AI_DELTA, "delta", text.substring(i, Math.min(text.length(), i + size)));
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String buildInput(ReplyContext ctx) {
        List<ChatMessage> history = ctx.getHistory();
        StringBuilder sb = new StringBuilder();
        if (history != null && history.size() > 1) {
            sb.append("【最近对话】\n");
            history.stream()
                    .filter(m -> !"AI".equals(m.getRole()) || m.getContent() != null)
                    .skip(Math.max(0, history.size() - 9L))
                    .forEach(m -> sb.append(switch (m.getRole()) {
                        case "USER" -> "用户：";
                        case "AI" -> "客服：";
                        default -> "系统：";
                    }).append(truncate(m.getContent(), 300)).append("\n"));
        }
        sb.append("【用户最新消息】\n").append(ctx.getUserContent());
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}