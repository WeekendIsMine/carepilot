package com.carepilot.agent.tools;

import com.carepilot.agent.AgentContext;
import com.carepilot.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.carepilot.chat.ws.FrameSink;
import com.carepilot.chat.ws.WsFrameType;
import com.carepilot.chat.ws.WsFrames;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 工具调用追踪：把「调了哪个工具、参数是什么、结果如何」包装成 WS 帧推给前端，
 * 用户能看到 AI 正在查什么，出问题也方便排查。
 */
public final class ToolTracer {

    private static final Logger log = LoggerFactory.getLogger(ToolTracer.class);

    private ToolTracer() {}

    public static String trace(String tool, Map<String, Object> args, Supplier<String> body) {
        FrameSink sink = AgentContext.sink();
        long start = System.currentTimeMillis();
        if (sink != null) {
            Map<String, Object> frame = WsFrames.of(WsFrameType.TOOL_CALL);
            frame.put("tool", tool);
            frame.put("status", "running");
            frame.put("args", args);
            sink.send(frame);
        }
        String result;
        try {
            result = body.get();
        } catch (Exception e) {
            // 技术异常（SQL、网络）不要把原文甩给用户，日志里留全量堆栈
            log.error("tool {} failed", tool, e);
            if (e instanceof BizException) {
                result = "工具执行失败：" + e.getMessage();
            } else {
                result = "工具暂时执行失败，请稍后重试；如果一直失败，可以转人工处理";
            }
        }
        if (sink != null) {
            Map<String, Object> frame = WsFrames.of(WsFrameType.TOOL_CALL);
            frame.put("tool", tool);
            frame.put("status", "done");
            frame.put("costMs", System.currentTimeMillis() - start);
            frame.put("result", result.length() > 300 ? result.substring(0, 300) + "…" : result);
            sink.send(frame);
        }
        return result;
    }
}