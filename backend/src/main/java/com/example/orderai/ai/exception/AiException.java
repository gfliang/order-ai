package com.example.orderai.ai.exception;

import com.example.orderai.common.exception.BusinessException;
import lombok.Getter;

import java.time.Duration;

/**
 * AI 服务异常：区分可重试与不可重试，向前端返回可理解的错误信息。
 * 不暴露 API Key、内部地址、厂商原始错误内容或堆栈。
 */
@Getter
public class AiException extends BusinessException {

    /** 是否允许有限重试 */
    private final boolean retryable;

    /** 服务端要求的重试等待时间，可为空 */
    private final Duration retryAfter;

    private AiException(int code, int httpStatus, String message, boolean retryable, Duration retryAfter) {
        super(code, httpStatus, message);
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public static AiException notConfigured() {
        return new AiException(50301, 503, "AI 服务未配置，请检查 QWEN_API_KEY 与 QWEN_BASE_URL", false, null);
    }

    public static AiException authFailed() {
        return new AiException(50203, 502, "AI 服务鉴权失败，请检查 API Key 配置", false, null);
    }

    public static AiException requestRejected() {
        return new AiException(50206, 502, "AI 请求参数不符合模型服务要求，请稍后重试", false, null);
    }

    public static AiException rateLimited(Duration retryAfter) {
        return new AiException(50202, 429, "AI 服务请求过于频繁，请稍后重试", true, retryAfter);
    }

    public static AiException timeout() {
        return new AiException(50201, 504, "AI 服务响应超时，请稍后重试", true, null);
    }

    public static AiException unavailable() {
        return new AiException(50204, 502, "AI 服务暂时不可用，请稍后重试", true, null);
    }

    public static AiException invalidResponse() {
        return new AiException(50205, 502, "AI 返回内容异常，请稍后重试", false, null);
    }
}
