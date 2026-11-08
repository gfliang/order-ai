package com.example.orderai.ai.service;

import com.example.orderai.ai.client.QwenModelClient;
import com.example.orderai.ai.client.StreamCancelledException;
import com.example.orderai.ai.exception.AiException;
import com.example.orderai.ai.model.AiChatRequest;
import com.example.orderai.ai.model.ChatMessage;
import com.example.orderai.ai.model.ToolCall;
import com.example.orderai.ai.model.ToolDefinition;
import com.example.orderai.ai.tool.AiToolCatalog;
import com.example.orderai.ai.tool.AiToolExecutor;
import com.example.orderai.ai.tool.ToolExecutionResult;
import com.example.orderai.common.context.UserContext;
import com.example.orderai.order.enums.OrderStatus;
import com.example.orderai.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话流式服务：在独立线程池中驱动模型流式调用与工具循环，
 * 通过 SseEmitter 推送 thinking / message / tool_call / tool_result / error / done 事件。
 * 注意：流式期间不持有数据库事务，工具执行结果由订单服务自行管理事务。
 */
@Slf4j
@Service
public class AiStreamService {

    /** SSE 连接最长存活时间，需大于模型整体超时 */
    private static final long EMITTER_TIMEOUT_MILLIS = 120_000L;

    /** 单次请求内最多允许的模型工具交互轮数，防止无限调用 */
    private static final int MAX_TOOL_ROUNDS = 5;

    private final QwenModelClient qwenModelClient;
    private final SystemPromptProvider systemPromptProvider;
    private final AiToolExecutor aiToolExecutor;
    private final ObjectMapper objectMapper;
    private final Executor streamExecutor;

    public AiStreamService(QwenModelClient qwenModelClient,
                           SystemPromptProvider systemPromptProvider,
                           AiToolExecutor aiToolExecutor,
                           ObjectMapper objectMapper,
                           @Qualifier("aiStreamExecutor") Executor streamExecutor) {
        this.qwenModelClient = qwenModelClient;
        this.systemPromptProvider = systemPromptProvider;
        this.aiToolExecutor = aiToolExecutor;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
    }

    public SseEmitter stream(AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        start(request, emitter);
        return emitter;
    }

    /** 绑定生命周期回调并提交到流式线程池；包级可见便于测试同步执行 */
    void start(AiChatRequest request, SseEmitter emitter) {
        String userId = UserContext.requireUserId();
        AtomicBoolean closed = new AtomicBoolean(false);

        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));

        try {
            streamExecutor.execute(() -> run(userId, request, emitter, closed));
        } catch (RuntimeException ex) {
            log.error("提交 AI 流式任务失败 userId={}", userId, ex);
            sendError(emitter, closed,
                    AiException.unavailable().getCode(), AiException.unavailable().getMessage());
        }
    }

    private void run(String userId, AiChatRequest request, SseEmitter emitter, AtomicBoolean closed) {
        // SSE 异步线程需要重新建立用户上下文
        UserContext.setUserId(userId);
        long startNanos = System.nanoTime();
        int round = 0;
        try {
            AiMessageValidator.validate(request.getMessages());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPromptProvider.getSystemPrompt()));
            // 前端历史只保留角色与文本，忽略可能伪造的工具调用字段
            request.getMessages().forEach(message ->
                    messages.add(new ChatMessage(message.getRole(), message.getContent())));
            List<ToolDefinition> tools = AiToolCatalog.definitions();

            sendThinking(emitter, closed, "正在理解你的问题...");

            for (round = 1; round <= MAX_TOOL_ROUNDS; round++) {
                ChatMessage assistant = qwenModelClient.streamChat(messages, tools,
                        delta -> sendEvent(emitter, closed, "message", Map.of("content", delta)),
                        closed::get);

                if (assistant.getToolCalls() == null || assistant.getToolCalls().isEmpty()) {
                    String content = assistant.getContent();
                    if (content == null || content.isBlank()) {
                        throw AiException.invalidResponse();
                    }
                    sendDone(emitter, closed, request.getConversationId());
                    log.info("AI流式对话完成 userId={} conversationId={} 上下文消息数={} 工具轮数={} "
                                    + "耗时={}ms",
                            userId, request.getConversationId(), request.getMessages().size(),
                            round, elapsedMillis(startNanos));
                    return;
                }

                messages.add(ChatMessage.assistantToolCalls(assistant.getContent(),
                        assistant.getToolCalls()));
                for (ToolCall toolCall : assistant.getToolCalls()) {
                    sendEvent(emitter, closed, "tool_call", toolEvent(toolCall));
                    ToolExecutionResult result = aiToolExecutor.execute(toolCall);
                    messages.add(ChatMessage.tool(toolCall.getId(), result.getContent()));
                    sendEvent(emitter, closed, "tool_result", toolResultEvent(toolCall, result));
                }
                sendThinking(emitter, closed, "正在整理工具结果...");
            }

            log.warn("AI流式工具调用轮数超限 userId={} conversationId={} maxRounds={}",
                    userId, request.getConversationId(), MAX_TOOL_ROUNDS);
            throw BusinessException.badRequest("操作步骤过多，已中止本次请求，请拆分需求后重试");
        } catch (StreamCancelledException ex) {
            log.info("SSE 客户端已断开 userId={} conversationId={} 已完成轮数={}",
                    userId, request.getConversationId(), round);
        } catch (BusinessException ex) {
            log.warn("AI流式对话业务失败 userId={} code={} message={}",
                    userId, ex.getCode(), ex.getMessage());
            sendError(emitter, closed, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("AI流式对话发生未预期异常 userId={}", userId, ex);
            sendError(emitter, closed, 50000, "系统繁忙，请稍后重试");
        } finally {
            log.debug("AI流式请求结束 userId={} conversationId={}",
                    userId, request.getConversationId());
            UserContext.clear();
        }
    }

    private void sendThinking(SseEmitter emitter, AtomicBoolean closed, String message) {
        sendEvent(emitter, closed, "thinking", Map.of("message", message));
    }

    private void sendDone(SseEmitter emitter, AtomicBoolean closed, String conversationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId == null ? "" : conversationId);
        sendEvent(emitter, closed, "done", payload);
        completeQuietly(emitter);
    }

    private Map<String, Object> toolEvent(ToolCall toolCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", toolCall.getId() == null ? "" : toolCall.getId());
        payload.put("name", toolName(toolCall));
        return payload;
    }

    /** 工具结果只推送摘要与状态，完整数据交由模型组织自然语言回复 */
    private Map<String, Object> toolResultEvent(ToolCall toolCall, ToolExecutionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", toolCall.getId() == null ? "" : toolCall.getId());
        payload.put("name", toolName(toolCall));

        JsonNode body = readJson(result.getContent());
        boolean success = body != null && body.path("success").asBoolean(false);
        payload.put("status", success ? "success" : "error");
        payload.put("summary", success ? successSummary(toolName(toolCall), body.path("data"))
                : failureMessage(body));
        return payload;
    }

    private String successSummary(String name, JsonNode data) {
        if (data.has("orderNo")) {
            String orderNo = data.path("orderNo").asText();
            String status = data.path("status").asText("");
            return status.isBlank() ? orderNo + " · 操作成功"
                    : orderNo + " · " + statusLabel(status);
        }
        if (data.has("records")) {
            long total = data.path("total").asLong(data.path("records").size());
            return total == 0 ? "未查询到匹配的订单" : "查询到 " + total + " 条订单";
        }
        if (data.has("totalOrders")) {
            return "已获取订单统计概览";
        }
        return AiToolCatalog.STATISTICS_ORDERS.equals(name) ? "已获取订单统计概览" : "工具执行成功";
    }

    private String statusLabel(String status) {
        try {
            return OrderStatus.of(status).getLabel();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }

    private String failureMessage(JsonNode body) {
        if (body == null) {
            return "工具执行失败";
        }
        String message = body.path("error").path("message").asText("");
        return message.isBlank() ? "工具执行失败" : message;
    }

    private JsonNode readJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (Exception ex) {
            log.warn("工具结果解析失败，按失败处理");
            return null;
        }
    }

    private String toolName(ToolCall toolCall) {
        return toolCall.getFunction() == null || toolCall.getFunction().getName() == null
                ? "" : toolCall.getFunction().getName();
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean closed, String name, Object payload) {
        if (closed.get()) {
            throw new StreamCancelledException();
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            closed.set(true);
            throw new StreamCancelledException();
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean closed, int code, String message) {
        try {
            if (!closed.get()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("code", code);
                payload.put("message", message);
                emitter.send(SseEmitter.event().name("error")
                        .data(payload, MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException ex) {
            closed.set(true);
        } finally {
            completeQuietly(emitter);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 客户端已断开或容器已完成响应
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
