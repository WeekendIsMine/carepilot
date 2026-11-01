package com.carepilot.chat.reply;

/**
 * 大模型报错翻译：把额度不足、限流、超时、鉴权失败这类异常翻成用户能看懂的中文。
 */
public final class LlmErrors {

    private LlmErrors() {}

    public static boolean looksLikeError(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.startsWith("Exception:") || t.startsWith("Error:")) return true;
        return t.contains("\"code\":\"Arrearage\"")
                || t.contains("AllocationQuota")
                || t.contains("Free quota exhausted")
                || t.contains("\"code\":\"Throttling\"")
                || t.contains("InvalidApiKey")
                || (t.contains("Access denied") && t.contains("good standing"));
    }

    public static String friendly(String raw) {
        String t = raw == null ? "" : raw.toLowerCase();
        if (t.contains("arrearage") || t.contains("overdue") || t.contains("good standing")) {
            return "AI 助手暂时不可用：模型账号余额不足，需要充值后才能继续。\n\n你可以先点右上角「转人工」，由人工客服继续处理。";
        }
        if (t.contains("quota") || t.contains("allocationquota") || t.contains("free tier")) {
            return "AI 助手的模型额度已用尽（免费额度用完或账户欠费）。\n\n需要管理员在阿里云百炼控制台充值、或关闭「仅用免费额度」模式后才能恢复。你可以先点右上角「转人工」，由人工客服继续处理。";
        }
        if (t.contains("throttling") || t.contains("rate limit") || t.contains("429")) {
            return "AI 请求太频繁了，请稍等几秒再发；如果一直这样，可以点右上角「转人工」。";
        }
        if (t.contains("timeout") || t.contains("timed out")) {
            return "AI 响应超时了，请重试一次；多次超时可以点右上角「转人工」。";
        }
        if (t.contains("unauthorized") || t.contains("invalidapikey") || t.contains("401")) {
            return "AI 服务鉴权失败，需要管理员检查模型配置。你可以先点右上角「转人工」。";
        }
        return "AI 服务暂时不可用，请稍后重试，或点右上角「转人工」由人工客服接手。";
    }

    public static String friendly(Throwable e) {
        return friendly(e == null ? null : e.getMessage());
    }

    public static String abbreviate(String text) {
        if (text == null) return "";
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() <= 300 ? one : one.substring(0, 300) + "…";
    }
}