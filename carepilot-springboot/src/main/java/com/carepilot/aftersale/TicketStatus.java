package com.carepilot.aftersale;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：定义每个动作允许的起始状态和目标状态，非法流转直接抛异常。
 * 状态：PENDING(待审) → APPROVED(通过) → REFUNDING(退款中) → DONE(完成)，
 * 另有 REJECTED(拒绝) 与 CLOSED(关闭) 两个终态。
 */
public final class TicketStatus {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REFUNDING = "REFUNDING";
    public static final String DONE = "DONE";
    public static final String CLOSED = "CLOSED";

    /**
     * 状态机表：每个动作能接受哪些「起始状态」、会走到哪个「目标状态」
     * 格式是 动作 -> [允许的起始状态集合, 目标状态]
     * 改流转规则只改这张表，业务代码不用动
     */
    public static final Map<String, List<Set<String>>> TRANSITIONS = Map.of(
            "APPROVE", List.of(Set.of(PENDING), Set.of(APPROVED)),
            "REJECT", List.of(Set.of(PENDING), Set.of(REJECTED)),
            "REFUND", List.of(Set.of(APPROVED), Set.of(REFUNDING)),
            "FINISH", List.of(Set.of(REFUNDING, APPROVED), Set.of(DONE)),
            "CLOSE", List.of(Set.of(PENDING, APPROVED, REJECTED, REFUNDING), Set.of(CLOSED))
    );

    public static final Set<String> TERMINAL = Set.of(DONE, CLOSED);

    private TicketStatus() {}

    public static String target(String action, String from) {
        List<Set<String>> t = TRANSITIONS.get(action);
        if (t == null) {
            throw new IllegalArgumentException("未知动作：" + action);
        }
        // 非法流转直接抛异常，比如已完成(DONE)的工单不允许再退款
        if (!t.get(0).contains(from)) {
            throw new IllegalStateException("工单当前状态 " + from + " 不能执行 " + action);
        }
        return t.get(1).iterator().next();
    }

    public static String label(String status) {
        return switch (status) {
            case PENDING -> "待审核";
            case APPROVED -> "已通过";
            case REJECTED -> "已拒绝";
            case REFUNDING -> "退款中";
            case DONE -> "已完成";
            case CLOSED -> "已关闭";
            default -> status;
        };
    }
}
