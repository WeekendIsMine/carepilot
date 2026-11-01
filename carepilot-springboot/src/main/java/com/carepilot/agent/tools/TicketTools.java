package com.carepilot.agent.tools;

import com.carepilot.aftersale.entity.AfterSaleTicket;
import com.carepilot.aftersale.service.TicketService;
import com.carepilot.agent.AgentContext;
import com.carepilot.agent.approval.ApprovalService;
import com.carepilot.agent.approval.ChatApproval;
import com.carepilot.chat.ws.FrameSink;
import com.carepilot.chat.ws.WsFrameType;
import com.carepilot.chat.ws.WsFrames;
import com.carepilot.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 给大模型用的售后工具：查我的工单、创建退换货申请。创建属于写操作，会走审批。
 */
@Component
@RequiredArgsConstructor
public class TicketTools {

    private final TicketService ticketService;
    private final OrderQueryService orderQueryService;
    private final ApprovalService approvalService;

    @Tool(description = "查询当前用户的售后工单记录")
    public String listMyTickets() {
        return ToolTracer.trace("listMyTickets", Map.of(), () -> {
            List<AfterSaleTicket> list = ticketService.listMine(AgentContext.userId(), 10);
            if (list.isEmpty()) return "该用户没有售后工单";
            StringBuilder sb = new StringBuilder("售后工单：");
            for (AfterSaleTicket t : list) {
                sb.append("\n- ").append(t.getTicketNo())
                  .append(" | 订单 ").append(t.getOrderNo())
                  .append(" | ").append(t.getType())
                  .append(" | ").append(t.getStatus());
            }
            return sb.toString();
        });
    }

    @Tool(description = """
            为用户申请售后（退款/退货/换货）。注意：调用本工具不会真的创建工单，
            只会生成一张待用户确认的审批单。系统会在界面上弹出确认卡，用户点"确认"后才真正建单。
            调用前必须先用 queryOrder 核实订单，并在回答里告诉用户审批单号。
            """)
    public String submitAfterSaleRequest(
            @ToolParam(description = "订单号") String orderNo,
            @ToolParam(description = "售后类型：REFUND退款 / RETURN退货 / EXCHANGE换货") String type,
            @ToolParam(description = "用户申请的原因，一句话") String reason) {
        return ToolTracer.trace("submitAfterSaleRequest",
                Map.of("orderNo", String.valueOf(orderNo), "type", String.valueOf(type)),
                () -> {
                    Long userId = AgentContext.userId();
                    Long sessionId = AgentContext.sessionId();

                    OrderQueryService.PolicyCheck check = orderQueryService.evaluate(userId, orderNo);
                    if (!check.isAllowed()) {
                        return "不能为该订单申请售后：" + check.getReason();
                    }
                    if (ticketService.hasActive(userId, orderNo)) {
                        return "该订单已有处理中的售后工单，请勿重复提交";
                    }

                    Map<String, Object> args = new LinkedHashMap<>();
                    args.put("orderNo", check.getOrderNo());
                    args.put("type", type);
                    args.put("reason", reason);
                    args.put("amount", check.getRefundAmount());
                    args.put("productName", check.getProductName());

                    ChatApproval approval = approvalService.request(
                            userId, sessionId, ApprovalService.TOOL_SUBMIT_AFTER_SALE, args);

                    FrameSink sink = AgentContext.sink();
                    if (sink != null) {
                        Map<String, Object> frame = WsFrames.of(WsFrameType.APPROVAL);
                        frame.put("approvalNo", approval.getApprovalNo());
                        frame.put("tool", ApprovalService.TOOL_SUBMIT_AFTER_SALE);
                        frame.put("args", args);
                        frame.put("expireAt", approval.getExpireAt().toString());
                        sink.send(frame);
                    }
                    return "已生成审批单 " + approval.getApprovalNo()
                            + "（订单 " + check.getOrderNo() + "，" + check.getProductName()
                            + "，可退金额 " + check.getRefundAmount() + " 元，判定依据：" + check.getReason()
                            + "）。请提示用户在界面上点击「确认提交」，确认后才会真正创建工单。";
                });
    }
}
