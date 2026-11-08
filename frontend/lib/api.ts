import axios from "axios";

export const USER_ID_STORAGE_KEY = "order_ai_user_id";

const RAW_API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

// Render 蓝图注入的是主机名（不含协议），这里统一补全，便于本地与线上共用同一套代码
export const API_BASE_URL = RAW_API_BASE_URL.startsWith("http")
  ? RAW_API_BASE_URL
  : `https://${RAW_API_BASE_URL}`;

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

// 统一注入 X-User-Id 请求头
api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const userId = window.localStorage.getItem(USER_ID_STORAGE_KEY);
    if (userId) {
      config.headers["X-User-Id"] = userId;
    }
  }
  return config;
});

export default api;
