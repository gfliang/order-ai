package com.example.orderai.common.context;

import com.example.orderai.common.response.ApiResponse;
import com.example.orderai.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * X-User-Id 用户上下文过滤器。
 * 白名单：创建用户 POST /api/users、验证用户 GET /api/users/{userId}、健康检查 GET /api/health。
 * 其余 /api/** 请求必须携带有效的 X-User-Id，否则返回 401。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class UserContextFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        // 用户创建与验证接口对白名单放行
        if (path.equals("/api/users") && "POST".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/users/") && "GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 健康检查供部署平台探活调用，无需用户身份
        return path.equals("/api/health") && "GET".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank() || !userService.exists(userId)) {
            writeUnauthorized(response);
            return;
        }
        try {
            UserContext.setUserId(userId);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(40100, "用户标识无效或缺失，请先初始化用户");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
