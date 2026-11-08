package com.example.orderai.common.controller;

import com.example.orderai.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口：供部署平台探活使用。
 * 不依赖数据库与 AI 服务，保证响应足够轻量。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }
}
