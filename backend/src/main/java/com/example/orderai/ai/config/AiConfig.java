package com.example.orderai.ai.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Qwen 模型调用所需的 WebClient 配置 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public WebClient qwenWebClient(AiProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeout().toMillis())
                .responseTimeout(properties.getResponseTimeout());

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));

        if (properties.isConfigured()) {
            builder.baseUrl(properties.getBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
        return builder.build();
    }

    /** SSE 推送专用线程池：模型流式调用会阻塞线程，不能占用 MVC/Reactor 线程 */
    @Bean(name = "aiStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "ai-stream-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
