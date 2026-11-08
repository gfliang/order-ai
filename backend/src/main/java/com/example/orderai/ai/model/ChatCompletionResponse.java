package com.example.orderai.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 兼容 OpenAI / DashScope compatible-mode 的对话响应结构，仅保留当前阶段需要的字段 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionResponse {

    private String id;
    private String model;
    private List<Choice> choices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {

        private int index;
        private ChatMessage message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }
}
