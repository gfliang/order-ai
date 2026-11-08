package com.example.orderai.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 交给模型的工具定义；parameters 为 JSON Schema */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    private String type;
    private FunctionDefinition function;

    public static ToolDefinition of(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition("function", new FunctionDefinition(name, description, parameters));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionDefinition {

        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
