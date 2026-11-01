package com.carepilot.aftersale.vo;

import com.carepilot.aftersale.entity.AfterSaleTicket;
import com.carepilot.mall.entity.RefundRecord;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TicketVO {
    private Long id;
    private String ticketNo;
    private String orderNo;
    private String type;
    private String reason;
    private String status;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    private String refundNo;

    private String refundStatus;
    private String refundChannel;
    private BigDecimal refundAmount;
    private LocalDateTime refundedAt;

    public static TicketVO of(AfterSaleTicket t, RefundRecord r) {
        TicketVO vo = TicketVO.builder()
                .id(t.getId())
                .ticketNo(t.getTicketNo())
                .orderNo(t.getOrderNo())
                .type(t.getType())
                .reason(t.getReason())
                .status(t.getStatus())
                .amount(t.getAmount())
                .createdAt(t.getCreatedAt())
                .build();
        if (r != null) {
            vo.setRefundNo(r.getRefundNo());
            vo.setRefundStatus(r.getStatus());
            vo.setRefundChannel(r.getChannel());
            vo.setRefundAmount(r.getAmount());
            vo.setRefundedAt(r.getRefundedAt());
        }
        return vo;
    }
}
