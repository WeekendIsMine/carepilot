package com.carepilot.agent.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提示词注册表：启动时加载 classpath:prompts/*.st，解析文件头的版本/日期/备注，按 key 取用。
 * 提示词改动不需要改代码，新增一个 .st 文件即可。
 */
@Slf4j
@Component
public class PromptRegistry {

    public record Prompt(String key, String version, String updated, String note, String content) {}

    private final Map<String, Prompt> prompts = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:prompts/*.st");
            for (Resource r : files) {
                String name = r.getFilename();
                if (name == null) continue;
                String key = name.replaceFirst("\\.st$", "");
                prompts.put(key, parse(key, StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8)));
            }
            log.info("prompts loaded: {}", prompts.entrySet().stream()
                    .map(e -> e.getKey() + "@" + e.getValue().version())
                    .toList());
        } catch (Exception e) {
            throw new IllegalStateException("加载提示词失败", e);
        }
    }

    public Prompt get(String key) {
        Prompt p = prompts.get(key);
        if (p == null) throw new IllegalStateException("提示词不存在: " + key);
        return p;
    }

    public String content(String key) {
        return get(key).content();
    }

    public String version(String key) {
        return get(key).version();
    }

    private Prompt parse(String key, String raw) {
        String text = raw.replace("\r\n", "\n");
        if (!text.startsWith("---")) {
            return new Prompt(key, "v0", null, null, text.trim());
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            return new Prompt(key, "v0", null, null, text.trim());
        }
        String header = text.substring(3, end);
        String body = text.substring(end + 4).trim();

        String version = "v0", updated = null, note = null;
        for (String line : header.split("\n")) {
            String s = line.trim();
            int i = s.indexOf(':');
            if (i <= 0) continue;
            String k = s.substring(0, i).trim();
            String v = s.substring(i + 1).trim();
            switch (k) {
                case "version" -> version = v;
                case "updated" -> updated = v;
                case "note" -> note = v;
                default -> { }
            }
        }
        return new Prompt(key, version, updated, note, body);
    }
}