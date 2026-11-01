package com.carepilot.chat.service;

import com.carepilot.aftersale.TicketStatus;
import com.carepilot.aftersale.service.TicketService;
import com.carepilot.chat.entity.ChatSession;
import com.carepilot.chat.vo.SessionContextVO;
import com.carepilot.chat.transfer.TransferService;
import com.carepilot.chat.vo.SessionVO;
import com.carepilot.order.entity.OrderItem;
import com.carepilot.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话上下文：把用户最近的订单、工单、排队位置组装成右侧栏要的数据。
 */
@Service
@RequiredArgsConstructor
public class SessionContextService {

    private static final int RECENT_ORDERS = 5;
    private static final int RECENT_TICKETS = 3;

    private final ChatSessionService sessionService;
    private final OrderQueryService orderQueryService;
    private final TicketService ticketService;
    private final TransferService transferService;

    /** 组装上下文，订单和工单都按当前用户过滤 */
    public SessionContextVO forSession(Long userId, Long sessionId) {
        ChatSession session = sessionService.getOwned(userId, sessionId);

        List<SessionContextVO.OrderBrief> orders = orderQueryService.listMine(userId, RECENT_ORDERS).stream()
                .map(o -> new SessionContextVO.OrderBrief(
                        o.getOrderNo(),
                        o.getStatus() == null ? -1 : o.getStatus(),
                        orderQueryService.statusName(o.getStatus()),
                        o.getPayAmount(),
                        firstName(orderQueryService.items(o.getId())),
                        o.getCreatedAt()))
                .toList();

        List<SessionContextVO.TicketBrief> tickets = ticketService.listMine(userId, RECENT_TICKETS).stream()
                .map(t -> new SessionContextVO.TicketBrief(
                        t.getTicketNo(), t.getOrderNo(), t.getType(), t.getStatus(),
                        TicketStatus.label(t.getStatus()), t.getCreatedAt()))
                .toList();

        Integer queuePosition = "WAITING".equals(session.getStatus())
                ? transferService.queuePosition(session.getPriority(), session.getId())
                : null;

        return new SessionContextVO(SessionVO.of(session), orders, tickets, queuePosition);
    }

    private String firstName(List<OrderItem> items) {
        if (items.isEmpty()) return "-";
        String name = items.get(0).getProductName();
        return items.size() > 1 ? name + " 等 " + items.size() + " 件" : name;
    }
}