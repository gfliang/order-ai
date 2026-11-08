package com.example.orderai.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 兼容 OpenAI / DashScope compatible-mode 的对话请求结构 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {

    private String model;
    private List<ChatMessage> messages;
    private boolean stream;
    private List<ToolDefinition> tools;

    public ChatCompletionRequest(String model, List<ChatMessage> messages, boolean stream) {
        this(model, messages, stream, null);
    }

    public ChatCompletionRequest(String model, List<ChatMessage> messages, boolean stream,
                                 List<ToolDefinition> tools) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
        this.tools = tools;
    }
}
