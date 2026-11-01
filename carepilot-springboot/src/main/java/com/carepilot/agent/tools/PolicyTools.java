package com.carepilot.agent.tools;

import com.carepilot.agent.AgentContext;
import com.carepilot.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 给大模型用的知识库工具：先检索条款再作答。返回内容带引用编号，前端展示来源。
 */
@Component
@RequiredArgsConstructor
public class PolicyTools {

    private final RetrievalService retrievalService;

    @Tool(description = """
            查询公司知识库，包含：售后政策条款（退货期限、运费、退款时效、换货规则、特殊类目等），
            以及店铺/服务信息（客服团队有哪些人、营业时间、联系方式、服务范围等）。
            凡是涉及「能不能退 / 多久能退 / 谁出运费 / 客服都有谁 / 你们怎么联系」这类事实，必须先查，
            不要凭印象回答，也不要在没查之前就拒绝。
            """)
    public String queryAfterSalePolicy(@ToolParam(description = "用户的问题或关键词") String question) {
        return ToolTracer.trace("queryAfterSalePolicy", Map.of("question", String.valueOf(question)), () -> {
            RetrievalService.Result r = retrievalService.retrieve(question, null);
            List<RetrievalService.Citation> citations = r.citations();
            if (r.weak() || citations.isEmpty()) {
                return "知识库中没有检索到可靠的政策条款（原因：" + r.debug()
                        + "）。请不要臆测条款，直接告诉用户你无法确认，并建议转人工。";
            }
            for (int i = 0; i < citations.size(); i++) {
                RetrievalService.Citation c = citations.get(i);
                AgentContext.addCitation(i + 1, c.docTitle(), c.titlePath(),
                        snippet(c.snippet()), c.score());
            }
            return r.context();
        });
    }

    private String snippet(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").trim();
        return t.length() <= 120 ? t : t.substring(0, 120) + "…";
    }
}