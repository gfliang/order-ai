package com.example.orderai.ai.service;

import com.example.orderai.ai.model.ChatMessage;
import com.example.orderai.common.exception.BusinessException;

import java.util.List;

/** 对话上下文校验：普通接口与 SSE 接口共用同一套规则 */
public final class AiMessageValidator {

    private static final int MAX_MESSAGES = 20;
    private static final int MAX_TOTAL_CONTENT = 10000;

    private AiMessageValidator() {
    }

    public static void validate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw BusinessException.badRequest("messages 不能为空");
        }
        if (messages.size() > MAX_MESSAGES) {
            throw BusinessException.badRequest("消息数量不能超过 " + MAX_MESSAGES + " 条");
        }
        ChatMessage last = messages.get(messages.size() - 1);
        if (!"user".equals(last.getRole())) {
            throw BusinessException.badRequest("最后一条消息必须来自用户");
        }
        boolean hasUserMessage = messages.stream().anyMatch(m -> "user".equals(m.getRole()));
        if (!hasUserMessage) {
            throw BusinessException.badRequest("至少需要一条用户消息");
        }
        int totalContent = messages.stream()
                .mapToInt(m -> m.getContent() == null ? 0 : m.getContent().length())
                .sum();
        if (totalContent > MAX_TOTAL_CONTENT) {
            throw BusinessException.badRequest("消息内容总长度不能超过 " + MAX_TOTAL_CONTENT + " 字符");
        }
    }
}
