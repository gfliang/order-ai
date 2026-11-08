package com.example.orderai.ai.client;

import com.example.orderai.ai.config.AiConfig;
import com.example.orderai.ai.config.AiProperties;
import com.example.orderai.ai.exception.AiException;
import com.example.orderai.ai.model.ChatCompletionResponse;
import com.example.orderai.ai.model.ChatMessage;
import com.example.orderai.ai.tool.AiToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QwenModelClientTest {

    private static final String SUCCESS_BODY = """
            {"id":"chatcmpl-1","model":"qwen-max",
             "choices":[{"index":0,"message":{"role":"assistant","content":" 你好，我是订单助手。 "},
             "finish_reason":"stop"}]}
            """;

    private static final String TOOL_CALL_BODY = """
            {"id":"chatcmpl-2","model":"qwen-max",
             "choices":[{"index":0,"message":{"role":"assistant","content":null,
             "tool_calls":[{"id":"call_1","type":"function",
             "function":{"name":"queryOrders","arguments":"{\\"status\\":\\"PENDING_PAYMENT\\"}"}}]},
             "finish_reason":"tool_calls"}]}
            """;

    private final AtomicInteger requestCount = new AtomicInteger();

    private HttpServer server;
    private AiProperties properties;
    private QwenModelClient client;

    private final List<ChatMessage> messages = List.of(ChatMessage.user("查询我的订单"));

    @BeforeEach
    void setUp() throws IOException {
        requestCount.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setModel("qwen-max");
        properties.setChatPath("/chat/completions");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(2));
        properties.setOverallTimeout(Duration.ofSeconds(6));
        properties.setMaxRetries(2);
        properties.setRetryBackoff(Duration.ofMillis(30));

        client = new QwenModelClient(properties, new AiConfig().qwenWebClient(properties),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void successReturnsTrimmedContent() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            send(exchange, 200, SUCCESS_BODY);
        });

        String reply = client.chat(messages);

        assertEquals("你好，我是订单助手。", reply);
        assertEquals(1, requestCount.get());
    }

    @Test
    void authFailureIsNotRetried() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            send(exchange, 401, "{\"error\":\"invalid api key\"}");
        });

        AiException ex = assertThrows(AiException.class, () -> client.chat(messages));

        assertEquals(50203, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void badRequestIsNotRetried() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            send(exchange, 400, "{\"error\":\"bad request\"}");
        });

        AiException ex = assertThrows(AiException.class, () -> client.chat(messages));

        assertEquals(50206, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void serverErrorIsRetriedThenSucceeds() {
        server.createContext("/chat/completions", exchange -> {
            int count = requestCount.incrementAndGet();
            if (count == 1) {
                send(exchange, 503, "{\"error\":\"busy\"}");
                return;
            }
            send(exchange, 200, SUCCESS_BODY);
        });

        String reply = client.chat(messages);

        assertEquals("你好，我是订单助手。", reply);
        assertEquals(2, requestCount.get());
    }

    @Test
    void rateLimitIsRetriedThenSucceeds() {
        server.createContext("/chat/completions", exchange -> {
            int count = requestCount.incrementAndGet();
            if (count == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                send(exchange, 429, "{\"error\":\"rate limited\"}");
                return;
            }
            send(exchange, 200, SUCCESS_BODY);
        });

        String reply = client.chat(messages);

        assertEquals("你好，我是订单助手。", reply);
        assertEquals(2, requestCount.get());
    }

    @Test
    void emptyChoicesIsInvalidResponseWithoutRetry() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            send(exchange, 200, "{\"id\":\"x\",\"choices\":[]}");
        });

        AiException ex = assertThrows(AiException.class, () -> client.chat(messages));

        assertEquals(50205, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void responseTimeoutIsReportedAndNotRetriedWhenRetriesDisabled() {
        properties.setMaxRetries(0);
        properties.setResponseTimeout(Duration.ofMillis(200));
        client = new QwenModelClient(properties, new AiConfig().qwenWebClient(properties),
                new ObjectMapper());

        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            sleepQuietly(800);
            send(exchange, 200, SUCCESS_BODY);
        });

        AiException ex = assertThrows(AiException.class, () -> client.chat(messages));

        assertEquals(50201, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void missingConfigurationFailsFast() {
        properties.setApiKey("");
        client = new QwenModelClient(properties, new AiConfig().qwenWebClient(properties),
                new ObjectMapper());

        AiException ex = assertThrows(AiException.class, () -> client.chat(messages));

        assertEquals(50301, ex.getCode());
        assertEquals(0, requestCount.get());
    }

    @Test
    void toolCallResponseIsParsed() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            send(exchange, 200, TOOL_CALL_BODY);
        });

        ChatCompletionResponse.Choice choice = client.chatWithTools(messages, AiToolCatalog.definitions());

        assertNull(choice.getMessage().getContent());
        assertEquals("call_1", choice.getMessage().getToolCalls().get(0).getId());
        assertEquals("queryOrders",
                choice.getMessage().getToolCalls().get(0).getFunction().getName());
        assertEquals("{\"status\":\"PENDING_PAYMENT\"}",
                choice.getMessage().getToolCalls().get(0).getFunction().getArguments());
        assertEquals(1, requestCount.get());
    }

    @Test
    void toolsAreIncludedInRequestPayload() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, SUCCESS_BODY);
        });

        client.chatWithTools(messages, AiToolCatalog.definitions());

        assertTrue(requestBody.get().contains("\"tools\""));
        assertTrue(requestBody.get().contains("\"queryOrders\""));
        assertTrue(requestBody.get().contains("\"statisticsOrders\""));
    }

    @Test
    void toolCallWithoutContentIsInvalidForPlainChat() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            send(exchange, 200, TOOL_CALL_BODY);
        });

        AiException ex = assertThrows(AiException.class, () -> client.chat(messages));

        assertEquals(50205, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void streamChatConcatenatesContentDeltasAndRequestsStream() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String sse = """
                data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"你"}}]}

                data: {"choices":[{"index":0,"delta":{"content":"好"}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendSse(exchange, 200, sse);
        });

        List<String> deltas = new ArrayList<>();
        ChatMessage reply = client.streamChat(messages, AiToolCatalog.definitions(),
                deltas::add, () -> false);

        assertEquals("你好", reply.getContent());
        assertNull(reply.getToolCalls());
        assertEquals(List.of("你", "好"), deltas);
        assertTrue(requestBody.get().contains("\"stream\":true"));
        assertTrue(requestBody.get().contains("\"tools\""));
    }

    @Test
    void streamChatMergesToolCallFragments() {
        String sse = """
                data: {"choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"queryOrders","arguments":""}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"status\\":"}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"PENDING_PAYMENT\\"}"}}]}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

                data: [DONE]

                """;
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            sendSse(exchange, 200, sse);
        });

        ChatMessage reply = client.streamChat(messages, AiToolCatalog.definitions(),
                delta -> { }, () -> false);

        assertNull(reply.getContent());
        assertEquals(1, reply.getToolCalls().size());
        assertEquals("call_1", reply.getToolCalls().get(0).getId());
        assertEquals("queryOrders", reply.getToolCalls().get(0).getFunction().getName());
        assertEquals("{\"status\":\"PENDING_PAYMENT\"}",
                reply.getToolCalls().get(0).getFunction().getArguments());
    }

    @Test
    void streamChatWithoutDataIsInvalidResponse() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            sendSse(exchange, 200, "data: [DONE]\n\n");
        });

        AiException ex = assertThrows(AiException.class, () -> client.streamChat(messages,
                AiToolCatalog.definitions(), delta -> { }, () -> false));

        assertEquals(50205, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void streamChatServerErrorIsMappedWithoutRetry() {
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            sendSse(exchange, 503, "{\"error\":\"busy\"}");
        });

        AiException ex = assertThrows(AiException.class, () -> client.streamChat(messages,
                AiToolCatalog.definitions(), delta -> { }, () -> false));

        assertEquals(50204, ex.getCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void streamChatStopsWhenCancelled() {
        String sse = """
                data: {"choices":[{"index":0,"delta":{"content":"第一段"}}]}

                data: {"choices":[{"index":0,"delta":{"content":"第二段"}}]}

                data: [DONE]

                """;
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            sendSse(exchange, 200, sse);
        });

        List<String> deltas = new ArrayList<>();
        AtomicInteger checks = new AtomicInteger();
        assertThrows(StreamCancelledException.class, () -> client.streamChat(messages,
                AiToolCatalog.definitions(), deltas::add,
                () -> checks.incrementAndGet() > 1));

        assertEquals(1, deltas.size());
    }

    private void sendSse(HttpExchange exchange, int status, String body) {
        sendWithContentType(exchange, status, body, "text/event-stream");
    }

    private void send(HttpExchange exchange, int status, String body) {
        sendWithContentType(exchange, status, body, "application/json");
    }

    private void sendWithContentType(HttpExchange exchange, int status, String body,
                                     String contentType) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            // 客户端超时断开时忽略写入失败
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
