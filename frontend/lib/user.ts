import axios from "axios";
import api, { USER_ID_STORAGE_KEY } from "./api";

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

async function createUser(): Promise<string> {
  const resp = await api.post<ApiResponse<{ userId: string }>>("/api/users");
  const userId = resp.data.data.userId;
  window.localStorage.setItem(USER_ID_STORAGE_KEY, userId);
  return userId;
}

/**
 * 确保当前浏览器已有有效的匿名用户：
 * 1. localStorage 无 userId -> 创建并保存
 * 2. 有 userId -> 调用验证接口，失效则重新创建
 */
export async function ensureUser(): Promise<string> {
  const cached = window.localStorage.getItem(USER_ID_STORAGE_KEY);
  if (!cached) {
    return createUser();
  }
  try {
    await api.get<ApiResponse<{ exists: boolean }>>(`/api/users/${cached}`);
    return cached;
  } catch (err) {
    // 仅在用户确实不存在（404）时重建；网络等错误不应误删本地标识
    if (axios.isAxiosError(err) && err.response?.status === 404) {
      window.localStorage.removeItem(USER_ID_STORAGE_KEY);
      return createUser();
    }
    throw err;
  }
}
