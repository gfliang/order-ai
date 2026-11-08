package com.example.orderai.user.controller;

import com.example.orderai.common.exception.BusinessException;
import com.example.orderai.common.response.ApiResponse;
import com.example.orderai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 创建匿名用户，返回 userId */
    @PostMapping
    public ApiResponse<Map<String, String>> createUser() {
        String userId = userService.createAnonymousUser();
        return ApiResponse.ok(Map.of("userId", userId));
    }

    /** 验证匿名用户是否存在 */
    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Boolean>> verifyUser(@PathVariable String userId) {
        if (!userService.exists(userId)) {
            throw BusinessException.notFound("用户不存在");
        }
        return ApiResponse.ok(Map.of("exists", true));
    }
}
