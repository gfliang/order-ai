package com.example.orderai.ai.client;

import com.example.orderai.ai.config.AiProperties;
import com.example.orderai.ai.exception.AiException;
import com.example.orderai.ai.model.ChatCompletionChunk;
import com.example.orderai.ai.model.ChatCompletionRequest;
import com.example.orderai.ai.model.ChatCompletionResponse;
import com.example.orderai.ai.model.ChatMessage;
import com.example.orderai.ai.model.ToolCall;
import com.example.orderai.ai.model.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Qwen-Max 统一调用入口：
 * 负责超时控制、异常识别与转换、有限重试与退避，以及必要的调用日志。
 * 不记录 API Key 与完整对话内容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenModelClient {

    private final AiProperties properties;
    private final WebClient qwenWebClient;
    private final ObjectMapper objectMapper;

    /** 普通对话：要求模型返回自然语言内容 */
    public String chat(List<ChatMessage> messages) {
        ChatCompletionResponse.Choice choice = chatWithTools(messages, null);
        String content = choice.getMessage().getContent();
        if (content == null || content.isBlank()) {
            throw AiException.invalidResponse();
        }
        return content.trim();
    }

    /** 带工具的对话：返回完整 choice，可能包含 tool_calls 而非文本内容 */
    public ChatCompletionResponse.Choice chatWithTools(List<ChatMessage> messages,
                                                     List<ToolDefinition> tools) {
        if (!properties.isConfigured()) {
            throw AiException.notConfigured();
        }

        ChatCompletionRequest payload = new ChatCompletionRequest(properties.getModel(), messages, false,
                tools == null || tools.isEmpty() ? null : tools);
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + properties.getOverallTimeout().toNanos();
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        AiException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }
                Duration wait = waitBeforeRetry(attempt - 1,
                        lastError == null ? null : lastError.getRetryAfter(), remainingNanos);
                log.warn("Qwen调用重试 requestId={} attempt={}/{} code={} wait={}ms",
                        requestId, attempt, maxAttempts, lastError.getCode(), wait.toMillis());
                sleep(wait);
            }
            try {
                ChatCompletionResponse.Choice choice = execute(payload, deadlineNanos);
                log.info("Qwen调用成功 requestId={} model={} attempts={} duration={}ms finishReason={}",
                        requestId, properties.getModel(), attempt, elapsedMillis(startNanos),
                        choice.getFinishReason());
                return choice;
            } catch (AiException ex) {
                lastError = ex;
                if (!ex.isRetryable()) {
                    log.warn("Qwen调用失败 requestId={} code={} attempts={} duration={}ms",
                            requestId, ex.getCode(), attempt, elapsedMillis(startNanos));
                    throw ex;
                }
            }
        }

        log.warn("Qwen调用失败且重试耗尽 requestId={} code={} duration={}ms",
                requestId, lastError == null ? null : lastError.getCode(), elapsedMillis(startNanos));
        throw lastError == null ? AiException.unavailable() : lastError;
    }

    /**
     * 流式对话：逐片推送文本增量，最终返回拼接好的助手消息（可能包含工具调用）。
     * 流式请求不做重试，避免已推送内容重复；客户端断开时通过 cancelled 主动中止。
     */
    public ChatMessage streamChat(List<ChatMessage> messages, List<ToolDefinition> tools,
                                  Consumer<String> onContentDelta, BooleanSupplier cancelled) {
        if (!properties.isConfigured()) {
            throw AiException.notConfigured();
        }

        ChatCompletionRequest payload = new ChatCompletionRequest(properties.getModel(), messages, true,
                tools == null || tools.isEmpty() ? null : tools);
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startNanos = System.nanoTime();
        StreamAccumulator accumulator = new StreamAccumulator();

        try {
            qwenWebClient.post()
                    .uri(properties.getChatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .timeout(properties.getResponseTimeout())
                    .doOnNext(event -> {
                        if (cancelled != null && cancelled.getAsBoolean()) {
                            throw new StreamCancelledException();
                        }
                        handleEvent(requestId, event, accumulator, onContentDelta);
                    })
                    .blockLast();
        } catch (WebClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (StreamCancelledException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeError(ex);
        }

        if (!accumulator.hasChunks()) {
            throw AiException.invalidResponse();
        }
        ChatMessage message = accumulator.toMessage();
        log.info("Qwen流式调用成功 requestId={} model={} 文本长度={} 工具调用数={} duration={}ms",
                requestId, properties.getModel(),
                message.getContent() == null ? 0 : message.getContent().length(),
                message.getToolCalls() == null ? 0 : message.getToolCalls().size(),
                elapsedMillis(startNanos));
        return message;
    }

    private void handleEvent(String requestId, ServerSentEvent<String> event,
                             StreamAccumulator accumulator, Consumer<String> onContentDelta) {
        String data = event.data();
        if (data == null || data.isBlank() || "[DONE]".equals(data.trim())) {
            return;
        }
        ChatCompletionChunk chunk;
        try {
            chunk = objectMapper.readValue(data, ChatCompletionChunk.class);
        } catch (JsonProcessingException ex) {
            log.warn("Qwen流式片段解析失败 requestId={} data={}", requestId, truncate(data, 120));
            return;
        }
        accumulator.append(chunk, onContentDelta);
    }

    private ChatCompletionResponse.Choice execute(ChatCompletionRequest payload, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw AiException.timeout();
        }

        ChatCompletionResponse response;
        try {
            response = qwenWebClient.post()
                    .uri(properties.getChatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(ChatCompletionResponse.class)
                    .timeout(Duration.ofNanos(remainingNanos))
                    .block();
        } catch (WebClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (RuntimeException ex) {
            throw mapRuntimeError(ex);
        }

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw AiException.invalidResponse();
        }
        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            throw AiException.invalidResponse();
        }
        ChatMessage message = choice.getMessage();
        if (message.getContent() != null) {
            message.setContent(message.getContent().trim());
        }
        return choice;
    }

    private AiException mapHttpError(WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        // 仅记录平台错误描述，便于排查配置或参数问题；不记录请求内容与密钥
        log.warn("Qwen请求失败 status={} body={}", status, truncate(ex.getResponseBodyAsString(), 300));
        if (status == 401 || status == 403) {
            return AiException.authFailed();
        }
        if (status == 429) {
            return AiException.rateLimited(parseRetryAfter(ex.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
        }
        if (status == 400) {
            return AiException.requestRejected();
        }
        if (status >= 500) {
            return AiException.unavailable();
        }
        return AiException.requestRejected();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private AiException mapRuntimeError(RuntimeException ex) {
        if (ex instanceof AiException aiException) {
            return aiException;
        }
        if (isTimeout(ex)) {
            return AiException.timeout();
        }
        if (hasCause(ex, WebClientRequestException.class)) {
            return AiException.unavailable();
        }
        log.error("Qwen调用发生未知异常", ex);
        return AiException.unavailable();
    }

    /** 覆盖 Reactor 的 TimeoutException 与 Netty 的 Connect/ReadTimeoutException */
    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().endsWith("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(header.trim())));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Duration waitBeforeRetry(int retryIndex, Duration retryAfter, long remainingNanos) {
        long baseMillis = properties.getRetryBackoff().toMillis() * (1L << (retryIndex - 1));
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, 300);
        long waitMillis = baseMillis + jitterMillis;
        if (retryAfter != null && !retryAfter.isZero()) {
            waitMillis = Math.max(waitMillis, retryAfter.toMillis());
        }
        // 留出至少 200ms 给下一次调用，避免等待耗尽整体时限
        long maxMillis = Math.max(0, remainingNanos / 1_000_000 - 200);
        return Duration.ofMillis(Math.min(waitMillis, maxMillis));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw AiException.unavailable();
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** 流式片段累积器：拼接文本增量，并按 index 合并跨片段的工具调用 */
    static final class StreamAccumulator {

        private final StringBuilder content = new StringBuilder();
        private final TreeMap<Integer, ToolCallBuilder> toolCallBuilders = new TreeMap<>();
        private boolean chunks;

        void append(ChatCompletionChunk chunk, Consumer<String> onContentDelta) {
            if (chunk == null || chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                return;
            }
            chunks = true;
            for (ChatCompletionChunk.Choice choice : chunk.getChoices()) {
                if (choice == null || choice.getDelta() == null) {
                    continue;
                }
                ChatCompletionChunk.Delta delta = choice.getDelta();
                if (delta.getContent() != null && !delta.getContent().isEmpty()) {
                    content.append(delta.getContent());
                    if (onContentDelta != null) {
                        onContentDelta.accept(delta.getContent());
                    }
                }
                appendToolCalls(delta.getToolCalls());
            }
        }

        private void appendToolCalls(List<ChatCompletionChunk.ToolCallDelta> deltas) {
            if (deltas == null) {
                return;
            }
            for (ChatCompletionChunk.ToolCallDelta delta : deltas) {
                if (delta == null) {
                    continue;
                }
                int index = delta.getIndex() == null ? 0 : delta.getIndex();
                ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, key -> new ToolCallBuilder());
                if (delta.getId() != null && !delta.getId().isBlank()) {
                    builder.id = delta.getId();
                }
                if (delta.getType() != null && !delta.getType().isBlank()) {
                    builder.type = delta.getType();
                }
                ChatCompletionChunk.FunctionDelta function = delta.getFunction();
                if (function != null) {
                    // 函数名通常只在首个片段出现，避免重复拼接
                    if (function.getName() != null && !function.getName().isBlank()
                            && builder.name == null) {
                        builder.name = function.getName();
                    }
                    if (function.getArguments() != null) {
                        builder.arguments.append(function.getArguments());
                    }
                }
            }
        }

        boolean hasChunks() {
            return chunks;
        }

        ChatMessage toMessage() {
            List<ToolCall> calls = new ArrayList<>();
            for (var entry : toolCallBuilders.entrySet()) {
                ToolCallBuilder builder = entry.getValue();
                if (builder.name == null || builder.name.isBlank()) {
                    continue;
                }
                String id = builder.id == null || builder.id.isBlank()
                        ? "call_" + entry.getKey() : builder.id;
                calls.add(new ToolCall(id, builder.type == null ? "function" : builder.type,
                        new ToolCall.FunctionCall(builder.name, builder.arguments.toString())));
            }
            String text = content.length() == 0 ? null : content.toString().trim();
            if (calls.isEmpty()) {
                return ChatMessage.assistant(text);
            }
            return ChatMessage.assistantToolCalls(text, calls);
        }
    }

    private static final class ToolCallBuilder {

        private String id;
        private String type;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}
