package com.example.orderai.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/** Qwen-Max 接入配置，超时与重试参数集中管理 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai.qwen")
public class AiProperties {

    private String baseUrl;
    private String apiKey;
    private String model = "qwen-max";
    private String chatPath = "/chat/completions";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(60);
    private Duration overallTimeout = Duration.ofSeconds(90);
    private int maxRetries = 2;
    private Duration retryBackoff = Duration.ofSeconds(1);

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
    }
}
