package com.example.orderai.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话消息：同时用于接口入参、模型请求与模型响应解析。
 * 接口入参只允许 user / assistant；system 与 tool 角色仅由后端内部构造。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    @NotBlank(message = "消息角色不能为空")
    @Pattern(regexp = "user|assistant", message = "消息角色只允许 user 或 assistant")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "单条消息内容不能超过 4000 字符")
    private String content;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    private String name;

    public ChatMessage(String role, String content) {
        this(role, content, null, null, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /** 模型发起的工具调用消息，需要原样回传给模型以保持调用链 */
    public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null, null);
    }

    /** 工具执行结果消息 */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, toolCallId, null);
    }
}
