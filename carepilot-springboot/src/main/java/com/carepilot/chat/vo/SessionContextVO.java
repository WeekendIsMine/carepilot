package com.carepilot.chat.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SessionContextVO(
        SessionVO session,
        List<OrderBrief> orders,
        List<TicketBrief> tickets,

        Integer queuePosition) {

    public record OrderBrief(String orderNo, int status, String statusName, BigDecimal amount,
                             String productName, LocalDateTime createdAt) {}

    public record TicketBrief(String ticketNo, String orderNo, String type, String status,
                              String statusLabel, LocalDateTime createdAt) {}
}