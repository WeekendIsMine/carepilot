package com.carepilot.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务单号生成：订单 CP、支付 PAY、退款 REF、工单 AS 等前缀 + 时间 + 随机位，
 * 比纯自增 id 更适合对外暴露。
 */
public final class Ids {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Ids() {}

    public static String of(String prefix) {
        return prefix + LocalDateTime.now().format(DAY)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    public static String session() { return of("CS"); }
    public static String approval() { return of("AP"); }
    public static String ticket() { return of("TK"); }
    public static String pay() { return of("PY"); }

    public static String trace() {
        return String.format("%08x", ThreadLocalRandom.current().nextInt());
    }
    public static String refund() { return of("RF"); }
}