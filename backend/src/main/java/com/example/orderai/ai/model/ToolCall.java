package com.example.orderai.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型返回的工具调用（OpenAI / DashScope compatible-mode 格式） */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    private String id;
    private String type;
    private FunctionCall function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {

        private String name;
        private String arguments;
    }
}
