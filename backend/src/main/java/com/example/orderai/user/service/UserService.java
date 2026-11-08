package com.example.orderai.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.orderai.user.entity.UserEntity;
import com.example.orderai.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /** 创建匿名用户，返回对外用户标识 */
    public String createAnonymousUser() {
        UserEntity user = new UserEntity();
        user.setUserId(generateUserId());
        userMapper.insert(user);
        return user.getUserId();
    }

    /** 校验匿名用户是否存在 */
    public boolean exists(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return userMapper.selectCount(
                new QueryWrapper<UserEntity>().eq("user_id", userId)) > 0;
    }

    private String generateUserId() {
        return "u_" + UUID.randomUUID().toString().replace("-", "");
    }
}
