package com.carepilot.agent.tools;

import com.carepilot.agent.AgentContext;
import com.carepilot.order.entity.LogisticsTrace;
import com.carepilot.order.entity.OrderItem;
import com.carepilot.order.entity.OrderMain;
import com.carepilot.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 给大模型用的订单工具：查我的订单、查物流。只读，不修改任何数据。
 */
@Component
@RequiredArgsConstructor
public class OrderTools {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderQueryService orderQueryService;

    @Tool(description = "按订单号查询当前用户的订单详情：状态、金额、商品、下单/发货/签收时间。查任何订单问题都必须先调用它，不要凭空回答")
    public String queryOrder(@ToolParam(description = "订单号，例如 CP202610180001") String orderNo) {
        return ToolTracer.trace("queryOrder", Map.of("orderNo", String.valueOf(orderNo)), () -> {
            Long userId = AgentContext.userId();
            OrderMain o = orderQueryService.findOwned(userId, orderNo);
            if (o == null) {
                return "未查询到订单 " + orderNo + "。请向用户确认订单号是否正确，不要猜测";
            }
            List<OrderItem> items = orderQueryService.items(o.getId());
            StringBuilder sb = new StringBuilder();
            sb.append("订单 ").append(o.getOrderNo())
              .append("：状态=").append(orderQueryService.statusName(o.getStatus()))
              .append("，实付=").append(o.getPayAmount());
            if (o.getPayTime() != null) sb.append("，下单时间=").append(o.getPayTime().format(FMT));
            if (o.getShipTime() != null) sb.append("，发货时间=").append(o.getShipTime().format(FMT));
            if (o.getReceiveTime() != null) sb.append("，签收时间=").append(o.getReceiveTime().format(FMT));
            sb.append("。商品：");
            for (OrderItem i : items) {
                sb.append(i.getProductName()).append(" ×").append(i.getQuantity()).append("；");
            }
            LogisticsTrace t = orderQueryService.logistics(o.getOrderNo());
            if (t != null) {
                if (t.getSignedTime() != null) sb.append("物流签收时间=").append(t.getSignedTime().format(FMT)).append("；");
                sb.append("当前物流=").append(t.getCurrentInfo());
            }
            return sb.toString();
        });
    }

    @Tool(description = "列出当前用户最近的订单（最多 10 条）。当用户说不出订单号时用它帮用户找")
    public String listMyOrders() {
        return ToolTracer.trace("listMyOrders", Map.of(), () -> {
            List<OrderMain> list = orderQueryService.listMine(AgentContext.userId(), 10);
            if (list.isEmpty()) return "该用户没有任何订单";
            StringBuilder sb = new StringBuilder("最近订单：");
            for (OrderMain o : list) {
                sb.append("\n- ").append(o.getOrderNo())
                  .append(" | ").append(orderQueryService.statusName(o.getStatus()))
                  .append(" | ").append(o.getPayAmount()).append(" 元");
            }
            return sb.toString();
        });
    }

    @Tool(description = "按订单号查询物流轨迹与签收时间")
    public String queryLogistics(@ToolParam(description = "订单号") String orderNo) {
        return ToolTracer.trace("queryLogistics", Map.of("orderNo", String.valueOf(orderNo)), () -> {
            OrderMain o = orderQueryService.findOwned(AgentContext.userId(), orderNo);
            if (o == null) return "未查询到订单 " + orderNo;
            LogisticsTrace t = orderQueryService.logistics(o.getOrderNo());
            if (t == null) return "订单 " + orderNo + " 暂无物流信息（可能还未发货）";
            StringBuilder sb = new StringBuilder("物流：" + t.getCarrier() + " " + t.getTrackingNo()
                    + "，当前状态：" + t.getCurrentInfo());
            if (t.getSignedTime() != null) sb.append("，签收时间=").append(t.getSignedTime().format(FMT));
            return sb.toString();
        });
    }
}
