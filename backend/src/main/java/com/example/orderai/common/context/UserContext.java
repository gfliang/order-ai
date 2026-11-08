package com.example.orderai.common.context;

/** 当前请求的用户上下文；请求结束必须清理，避免线程复用串用 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(String userId) {
        CURRENT_USER.set(userId);
    }

    public static String getUserId() {
        return CURRENT_USER.get();
    }

    /** 获取当前用户，未设置时视为未授权 */
    public static String requireUserId() {
        String userId = CURRENT_USER.get();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("当前请求缺少用户上下文");
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
