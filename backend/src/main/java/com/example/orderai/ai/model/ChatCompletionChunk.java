package com.example.orderai.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 流式响应片段（OpenAI / DashScope compatible-mode 格式） */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionChunk {

    private List<Choice> choices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {

        private Integer index;
        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {

        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCallDelta> toolCalls;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallDelta {

        private Integer index;
        private String id;
        private String type;
        private FunctionDelta function;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionDelta {

        private String name;
        private String arguments;
    }
}
