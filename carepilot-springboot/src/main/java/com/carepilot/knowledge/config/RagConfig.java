package com.carepilot.knowledge.config;

import com.carepilot.knowledge.RagProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 检索用 RestClient：单独设超时，避免精排/向量接口拖垮主线程。
 */
@Configuration
public class RagConfig {

    /** 带连接与读取超时的 RestClient，RAG 相关 HTTP 调用都复用它 */
    @Bean(name = "kbRestClient")
    public RestClient kbRestClient(RagProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(Math.max(2000, props.getRerank().getTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
