package com.example.orderai.ai.service;

import com.example.orderai.ai.client.QwenModelClient;
import com.example.orderai.ai.exception.AiException;
import com.example.orderai.ai.model.AiChatRequest;
import com.example.orderai.ai.model.ChatMessage;
import com.example.orderai.ai.model.ToolCall;
import com.example.orderai.ai.tool.AiToolExecutor;
import com.example.orderai.ai.tool.ToolExecutionResult;
import com.example.orderai.common.context.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStreamServiceTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Mock
    private QwenModelClient qwenModelClient;

    @Mock
    private SystemPromptProvider systemPromptProvider;

    @Mock
    private AiToolExecutor aiToolExecutor;

    private AiStreamService service;

    @BeforeEach
    void setUp() {
        UserContext.setUserId("demo-user-a");
        service = new AiStreamService(qwenModelClient, systemPromptProvider, aiToolExecutor,
                new ObjectMapper(), DIRECT_EXECUTOR);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void textReplyEmitsThinkingMessageDeltasAndDone() {
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");
        when(qwenModelClient.streamChat(anyList(), anyList(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<String> onDelta = invocation.getArgument(2);
            onDelta.accept("你");
            onDelta.accept("好");
            return ChatMessage.assistant("你好");
        });

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request("你好", "conv-1"), emitter);

        assertEquals(List.of("thinking", "message", "message", "done"), emitter.getEventNames());
        assertEquals("你", payload(emitter, 1).get("content"));
        assertEquals("好", payload(emitter, 2).get("content"));
        assertEquals("conv-1", payload(emitter, 3).get("conversationId"));
        assertTrue(emitter.isCompleted());
    }

    @Test
    void readToolEmitsToolCallAndSuccessResult() {
        ToolCall toolCall = toolCall("call-1", "queryOrders", "{\"status\":\"PENDING_PAYMENT\"}");
        ChatMessage toolMessage = ChatMessage.assistantToolCalls(null, List.of(toolCall));
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");
        when(qwenModelClient.streamChat(anyList(), anyList(), any(), any()))
                .thenReturn(toolMessage)
                .thenReturn(ChatMessage.assistant("你有一笔待付款订单。"));
        when(aiToolExecutor.execute(toolCall)).thenReturn(ToolExecutionResult.ofContent(
                "{\"success\":true,\"data\":{\"records\":[{},{}],\"total\":2}}"));

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request("我有没有待付款订单", "conv-2"), emitter);

        assertEquals(List.of("thinking", "tool_call", "tool_result", "thinking", "done"),
                emitter.getEventNames());
        assertEquals("queryOrders", payload(emitter, 1).get("name"));
        assertEquals("call-1", payload(emitter, 1).get("toolCallId"));
        assertEquals("success", payload(emitter, 2).get("status"));
        assertEquals("查询到 2 条订单", payload(emitter, 2).get("summary"));
    }

    @Test
    void writeToolEmitsSuccessResultAndDoneHasNoPendingActions() {
        ToolCall toolCall = toolCall("call-2", "cancelOrder", "{\"orderNo\":\"ORD10001\"}");
        ChatMessage toolMessage = ChatMessage.assistantToolCalls(null, List.of(toolCall));
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");
        when(qwenModelClient.streamChat(anyList(), anyList(), any(), any()))
                .thenReturn(toolMessage)
                .thenReturn(ChatMessage.assistant("订单 ORD10001 已取消。"));
        when(aiToolExecutor.execute(toolCall)).thenReturn(ToolExecutionResult.ofContent(
                "{\"success\":true,\"data\":{\"orderNo\":\"ORD10001\",\"status\":\"CANCELLED\"}}"));

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request("取消订单 ORD10001", "conv-3"), emitter);

        assertEquals(List.of("thinking", "tool_call", "tool_result", "thinking", "done"),
                emitter.getEventNames());
        assertEquals("success", payload(emitter, 2).get("status"));
        assertEquals("ORD10001 · 已取消", payload(emitter, 2).get("summary"));
        assertEquals("conv-3", payload(emitter, 4).get("conversationId"));
        assertTrue(!payload(emitter, 4).containsKey("pendingActions"));
    }

    @Test
    void toolErrorIsReportedAsErrorStatus() {
        ToolCall toolCall = toolCall("call-3", "queryOrders", "{}");
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");
        when(qwenModelClient.streamChat(anyList(), anyList(), any(), any()))
                .thenReturn(ChatMessage.assistantToolCalls(null, List.of(toolCall)))
                .thenReturn(ChatMessage.assistant("没有查到。"));
        when(aiToolExecutor.execute(toolCall)).thenReturn(ToolExecutionResult.ofContent(
                "{\"success\":false,\"error\":{\"code\":40000,\"message\":\"订单状态无效\"}}"));

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request("查询", "conv-4"), emitter);

        assertEquals("error", payload(emitter, 2).get("status"));
        assertEquals("订单状态无效", payload(emitter, 2).get("summary"));
    }

    @Test
    void modelFailureEmitsErrorEventAndCompletes() {
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");
        when(qwenModelClient.streamChat(anyList(), anyList(), any(), any()))
                .thenThrow(AiException.timeout());

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request("你好", "conv-5"), emitter);

        assertEquals(List.of("thinking", "error"), emitter.getEventNames());
        assertEquals(50201, payload(emitter, 1).get("code"));
        assertTrue(emitter.isCompleted());
    }

    @Test
    void invalidContextEmitsErrorWithoutThinking() {
        AiChatRequest request = new AiChatRequest();
        request.setConversationId("conv-6");
        request.setMessages(List.of(ChatMessage.assistant("欢迎使用")));

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request, emitter);

        assertEquals(List.of("error"), emitter.getEventNames());
        assertEquals(40000, payload(emitter, 0).get("code"));
    }

    @Test
    void toolLoopStopsAtMaxRounds() {
        ToolCall toolCall = toolCall("call-loop", "statisticsOrders", "{}");
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");
        when(qwenModelClient.streamChat(anyList(), anyList(), any(), any()))
                .thenReturn(ChatMessage.assistantToolCalls(null, List.of(toolCall)));
        when(aiToolExecutor.execute(any(ToolCall.class)))
                .thenReturn(ToolExecutionResult.ofContent("{\"success\":true,\"data\":{}}"));

        RecordingEmitter emitter = new RecordingEmitter();
        service.start(request("统计我的订单", "conv-7"), emitter);

        verify(qwenModelClient, times(5)).streamChat(anyList(), anyList(), any(), any());
        verify(aiToolExecutor, times(5)).execute(any(ToolCall.class));
        assertEquals("error", emitter.getEventNames().get(emitter.getEventNames().size() - 1));
        assertEquals(40000, payload(emitter, emitter.getEventNames().size() - 1).get("code"));
    }

    @Test
    void disconnectedClientStopsStreamingSilently() {
        when(systemPromptProvider.getSystemPrompt()).thenReturn("SYS-PROMPT");

        RecordingEmitter emitter = new RecordingEmitter() {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("broken pipe");
            }
        };
        service.start(request("你好", "conv-8"), emitter);

        assertTrue(emitter.getEventNames().isEmpty());
    }

    private AiChatRequest request(String userMessage, String conversationId, ChatMessage... history) {
        AiChatRequest request = new AiChatRequest();
        request.setConversationId(conversationId);
        List<ChatMessage> messages = new ArrayList<>();
        for (ChatMessage message : history) {
            messages.add(message);
        }
        if (userMessage != null) {
            messages.add(ChatMessage.user(userMessage));
        }
        request.setMessages(messages);
        return request;
    }

    private Map<String, Object> payload(RecordingEmitter emitter, int index) {
        return emitter.payloadAt(index);
    }

    private ToolCall toolCall(String id, String name, String arguments) {
        return new ToolCall(id, "function", new ToolCall.FunctionCall(name, arguments));
    }

    /** 直接收集事件名与 payload，避免依赖真实 HTTP 容器 */
    private static class RecordingEmitter extends SseEmitter {

        private final List<String> eventNames = new ArrayList<>();
        private final List<Object> payloads = new ArrayList<>();
        private boolean completed;

        RecordingEmitter() {
            super(120_000L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            String name = null;
            Object payload = null;
            for (ResponseBodyEmitter.DataWithMediaType entry : builder.build()) {
                Object data = entry.getData();
                if (data instanceof String text) {
                    // 元数据字符串形如 "event:thinking\ndata:"，逐行解析事件名
                    for (String line : text.split("\n")) {
                        if (line.startsWith("event:")) {
                            name = line.substring("event:".length()).trim();
                        }
                    }
                } else {
                    payload = data;
                }
            }
            eventNames.add(name);
            payloads.add(payload);
        }

        @Override
        public void complete() {
            completed = true;
            super.complete();
        }

        List<String> getEventNames() {
            return eventNames;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payloadAt(int index) {
            return (Map<String, Object>) payloads.get(index);
        }

        boolean isCompleted() {
            return completed;
        }
    }
}
