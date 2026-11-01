package com.carepilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步线程池：给 AI 回复等耗时任务用，避免占满 Tomcat 线程。
 */
@Configuration
public class AsyncConfig {

    /** 聊天专用线程池；队列满时由调用方降级为同步执行 */
    @Bean(name = "chatExecutor")
    public Executor chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
