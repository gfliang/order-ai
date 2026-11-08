package com.example.orderai.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** AI 对话接口入参，messages 为前端维护的当前会话上下文 */
@Data
public class AiChatRequest {

    @Size(max = 64, message = "conversationId 不能超过 64 字符")
    private String conversationId;

    @NotEmpty(message = "不能为空")
    @Size(max = 20, message = "消息数量不能超过 20 条")
    @Valid
    private List<ChatMessage> messages;
}
