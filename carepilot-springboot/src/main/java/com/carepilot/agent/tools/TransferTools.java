package com.carepilot.agent.tools;

import com.carepilot.agent.AgentContext;
import com.carepilot.chat.transfer.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 给大模型用的转人工工具：用户要求找客服时调用，把会话切到排队状态。
 */
@Component
@RequiredArgsConstructor
public class TransferTools {

    private final TransferService transferService;

    @Tool(description = """
            把当前会话转接给人工客服。仅在这几种情况调用：
            用户明确要求转人工、用户情绪激烈或投诉、你确实无法处理（例如需要人工核实签收异常）。
            调用后系统会排队分配坐席，并把你已经掌握的信息交给坐席。
            """)
    public String transferToHuman(
            @ToolParam(description = "转接原因，一句话") String reason,
            @ToolParam(description = "原因分类：COMPLAINT投诉 / EMOTION情绪 / REFUND退款 / POLICY政策 / OTHER其他") String reasonCode) {
        return ToolTracer.trace("transferToHuman", Map.of("reason", String.valueOf(reason)), () -> {
            int pos = transferService.requestHuman(
                    AgentContext.userId(), AgentContext.sessionId(),
                    reason == null ? "用户要求转人工" : reason,
                    reasonCode == null ? "OTHER" : reasonCode);
            return "已为用户转接人工客服，当前排队第 " + pos + " 位。请告诉用户稍候，并简要复述已掌握的信息。";
        });
    }
}
