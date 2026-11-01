package com.carepilot.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 的可调参数：工具最大迭代次数、每轮注入的历史条数等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "carepilot.agent")
public class AgentProperties {

    private int maxToolIterations = 6;

    private int chunkSize = 12;
}
