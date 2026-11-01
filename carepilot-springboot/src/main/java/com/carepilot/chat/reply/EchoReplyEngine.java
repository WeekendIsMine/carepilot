package com.carepilot.chat.reply;

import com.carepilot.chat.ws.WsFrameType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 回声引擎：不调大模型，只把用户的话原样流式打回去。
 * 用于没有 API Key 或只想测 WS 帧协议的场景（carepilot.reply.engine=echo）。
 */
@Component
@ConditionalOnProperty(name = "carepilot.reply.engine", havingValue = "echo")
public class EchoReplyEngine implements ReplyEngine {

    @Override
    public ReplyResult reply(ReplyContext ctx) throws Exception {
        String answer = "（回声模式）我收到了：" + ctx.getUserContent()
                + " · 会话 " + ctx.getSessionId() + " · 历史 " + ctx.getHistory().size() + " 条";
        StringBuilder sb = new StringBuilder();
        for (String token : answer.split("(?<=\\s)|(?=[，。·])")) {
            sb.append(token);
            ctx.getSink().send(WsFrameType.AI_DELTA, "delta", token);
            Thread.sleep(15);
        }
        return ReplyResult.of(sb.toString());
    }
}