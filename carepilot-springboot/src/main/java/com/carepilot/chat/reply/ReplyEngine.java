package com.carepilot.chat.reply;

/**
 * 回复引擎接口。实现可以是大模型，也可以是回声（测试用），ChatService 只依赖这个接口。
 */
public interface ReplyEngine {

    ReplyResult reply(ReplyContext ctx) throws Exception;
}
