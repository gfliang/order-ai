package com.example.orderai.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 对话接口返回结构；对话历史不落库，由前端维护 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseVO {

    private String conversationId;
    private String reply;
}
