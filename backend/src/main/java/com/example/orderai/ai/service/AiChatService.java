package com.example.orderai.ai.service;

import com.example.orderai.ai.client.QwenModelClient;
import com.example.orderai.ai.exception.AiException;
import com.example.orderai.ai.model.AiChatRequest;
import com.example.orderai.ai.model.AiChatResponseVO;
import com.example.orderai.ai.model.ChatCompletionResponse;
import com.example.orderai.ai.model.ChatMessage;
import com.example.orderai.ai.model.ToolCall;
import com.example.orderai.ai.model.ToolDefinition;
import com.example.orderai.ai.tool.AiToolCatalog;
import com.example.orderai.ai.tool.AiToolExecutor;
import com.example.orderai.ai.tool.ToolExecutionResult;
import com.example.orderai.common.context.UserContext;
import com.example.orderai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话应用服务：组装系统提示词与前端上下文、校验消息、驱动模型工具调用循环。
 * 对话历史不落库；用户身份来自后端上下文，不接受前端自行指定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 单次请求内最多允许的模型工具交互轮数，防止无限调用 */
    private static final int MAX_TOOL_ROUNDS = 5;

    private final QwenModelClient qwenModelClient;
    private final SystemPromptProvider systemPromptProvider;
    private final AiToolExecutor aiToolExecutor;

    public AiChatResponseVO chat(AiChatRequest request) {
        String userId = UserContext.requireUserId();
        AiMessageValidator.validate(request.getMessages());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPromptProvider.getSystemPrompt()));
        // 前端历史只保留角色与文本，忽略可能伪造的工具调用字段
        request.getMessages().forEach(message ->
                messages.add(new ChatMessage(message.getRole(), message.getContent())));

        List<ToolDefinition> tools = AiToolCatalog.definitions();
        long startNanos = System.nanoTime();

        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            ChatCompletionResponse.Choice choice = qwenModelClient.chatWithTools(messages, tools);
            ChatMessage assistant = choice.getMessage();

            if (assistant.getToolCalls() == null || assistant.getToolCalls().isEmpty()) {
                String content = assistant.getContent();
                if (content == null || content.isBlank()) {
                    throw AiException.invalidResponse();
                }
                log.info("AI对话完成 userId={} conversationId={} 上下文消息数={} 工具轮数={} 耗时={}ms",
                        userId, request.getConversationId(), request.getMessages().size(), round,
                        (System.nanoTime() - startNanos) / 1_000_000);
                return new AiChatResponseVO(request.getConversationId(), content.trim());
            }

            // 保留模型的工具调用消息，再逐条追加工具执行结果
            messages.add(ChatMessage.assistantToolCalls(assistant.getContent(), assistant.getToolCalls()));
            for (ToolCall toolCall : assistant.getToolCalls()) {
                ToolExecutionResult result = aiToolExecutor.execute(toolCall);
                messages.add(ChatMessage.tool(toolCall.getId(), result.getContent()));
            }
        }

        log.warn("AI工具调用轮数超限 userId={} conversationId={} maxRounds={}",
                userId, request.getConversationId(), MAX_TOOL_ROUNDS);
        throw BusinessException.badRequest("操作步骤过多，已中止本次请求，请拆分需求后重试");
    }
}
