package com.example.orderai.ai.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 加载系统提示词，集中维护 AI 角色与业务边界 */
@Component
public class SystemPromptProvider {

    private static final String PROMPT_LOCATION = "prompts/system-prompt.txt";

    private final String systemPrompt;

    public SystemPromptProvider() {
        this.systemPrompt = load();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    private String load() {
        ClassPathResource resource = new ClassPathResource(PROMPT_LOCATION);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("加载系统提示词失败: " + PROMPT_LOCATION, ex);
        }
    }
}
