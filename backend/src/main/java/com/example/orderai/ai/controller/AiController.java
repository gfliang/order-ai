package com.example.orderai.ai.controller;

import com.example.orderai.ai.model.AiChatRequest;
import com.example.orderai.ai.model.AiChatResponseVO;
import com.example.orderai.ai.service.AiChatService;
import com.example.orderai.ai.service.AiStreamService;
import com.example.orderai.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;
    private final AiStreamService aiStreamService;

    /** 普通对话接口（非流式） */
    @PostMapping("/chat")
    public ApiResponse<AiChatResponseVO> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.ok(aiChatService.chat(request));
    }

    /** SSE 流式对话：事件类型见设计文档 §14 */
    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiChatRequest request) {
        return aiStreamService.stream(request);
    }
}
