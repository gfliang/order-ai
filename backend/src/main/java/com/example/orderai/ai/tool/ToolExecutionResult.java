package com.example.orderai.ai.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工具执行结果：模型可见的 JSON 内容 */
@Getter
@AllArgsConstructor
public class ToolExecutionResult {

    private final String content;

    public static ToolExecutionResult ofContent(String content) {
        return new ToolExecutionResult(content);
    }
}
