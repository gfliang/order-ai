import { USER_ID_STORAGE_KEY } from "./api";

/** 发送给后端的会话上下文消息，仅保留角色与文本 */
export interface ChatHistoryMessage {
  role: "user" | "assistant";
  content: string;
}

/** 工具执行状态事件 */
export interface ToolEvent {
  toolCallId: string;
  name: string;
  status: "running" | "success" | "error";
  summary?: string;
}

export interface StreamHandlers {
  onThinking?: (message: string) => void;
  onMessage?: (content: string) => void;
  onToolCall?: (event: { toolCallId: string; name: string }) => void;
  onToolResult?: (event: ToolEvent) => void;
  onError?: (code: number, message: string) => void;
  onDone?: (payload: { conversationId: string }) => void;
}

/** 与后端 AiMessageValidator 保持一致的上限，避免请求被拒绝 */
export const MAX_CONTEXT_MESSAGES = 20;
export const MAX_CONTEXT_CHARS = 10000;
export const MAX_SINGLE_MESSAGE_CHARS = 4000;

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

/** 构造本轮请求上下文：保留最近的完整对话，并裁剪到后端允许的上限 */
export function buildContext(
  history: ChatHistoryMessage[],
  userMessage: string,
): ChatHistoryMessage[] {
  return trimHistory([
    ...history,
    { role: "user", content: userMessage.slice(0, MAX_SINGLE_MESSAGE_CHARS) },
  ]);
}

/** 裁剪历史消息：限制条数与总长度，丢弃最早的轮次 */
export function trimHistory(history: ChatHistoryMessage[]): ChatHistoryMessage[] {
  let items = history
    .filter((item) => item.content.trim().length > 0)
    .map((item) => ({
      role: item.role,
      content: item.content.slice(0, MAX_SINGLE_MESSAGE_CHARS),
    }));

  while (items.length > MAX_CONTEXT_MESSAGES) {
    items.shift();
  }

  let totalChars = items.reduce((sum, item) => sum + item.content.length, 0);
  while (totalChars > MAX_CONTEXT_CHARS && items.length > 1) {
    totalChars -= items.shift()!.content.length;
  }

  // 上下文从用户消息开始，保证模型看到完整的一轮问答
  while (items.length > 1 && items[0].role === "assistant") {
    items.shift();
  }
  return items;
}

/** POST + SSE 读取流式对话；事件协议见设计文档 §14 */
export async function streamChat(
  request: { conversationId: string; messages: ChatHistoryMessage[] },
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const userId =
    typeof window === "undefined" ? "" : window.localStorage.getItem(USER_ID_STORAGE_KEY) ?? "";

  const response = await fetch(`${API_BASE_URL}/api/ai/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(userId ? { "X-User-Id": userId } : {}),
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  if (!response.body) {
    throw new Error("服务器未返回流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      buffer = buffer.replace(/\r\n/g, "\n");

      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        dispatchEventBlock(block, handlers);
        boundary = buffer.indexOf("\n\n");
      }
    }

    const rest = buffer.trim();
    if (rest) {
      dispatchEventBlock(rest, handlers);
    }
  } finally {
    reader.releaseLock();
  }
}

function dispatchEventBlock(block: string, handlers: StreamHandlers): void {
  let eventName = "message";
  const dataLines: string[] = [];

  for (const rawLine of block.split("\n")) {
    if (rawLine.startsWith("event:")) {
      eventName = rawLine.slice(6).trim();
    } else if (rawLine.startsWith("data:")) {
      dataLines.push(rawLine.slice(5).replace(/^\s/, ""));
    }
  }

  if (dataLines.length === 0) {
    return;
  }

  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(dataLines.join("\n")) as Record<string, unknown>;
  } catch {
    return;
  }

  switch (eventName) {
    case "thinking":
      handlers.onThinking?.(String(payload.message ?? ""));
      break;
    case "message":
      handlers.onMessage?.(String(payload.content ?? ""));
      break;
    case "tool_call":
      handlers.onToolCall?.({
        toolCallId: String(payload.toolCallId ?? ""),
        name: String(payload.name ?? ""),
      });
      break;
    case "tool_result":
      handlers.onToolResult?.({
        toolCallId: String(payload.toolCallId ?? ""),
        name: String(payload.name ?? ""),
        status: (payload.status as ToolEvent["status"]) ?? "success",
        summary: payload.summary == undefined ? undefined : String(payload.summary),
      });
      break;
    case "error":
      handlers.onError?.(
        Number(payload.code ?? 50000),
        String(payload.message ?? "AI 服务暂时不可用"),
      );
      break;
    case "done":
      handlers.onDone?.({
        conversationId: String(payload.conversationId ?? ""),
      });
      break;
    default:
      break;
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  const fallback = `请求失败（HTTP ${response.status}）`;
  try {
    const text = await response.text();
    const json = JSON.parse(text) as { message?: string };
    return json.message?.trim() || fallback;
  } catch {
    return fallback;
  }
}

/** 工具名称 -> 中文展示 */
export const TOOL_LABELS: Record<string, string> = {
  queryOrders: "查询订单",
  statisticsOrders: "订单统计",
  createOrder: "创建订单",
  updateOrder: "修改订单",
  cancelOrder: "取消订单",
  shipOrder: "确认发货",
};

export function toolLabel(name: string): string {
  return TOOL_LABELS[name] ?? name;
}

/** 预置问题快捷入口，按业务类别分组 */
export const QUICK_QUESTIONS: { category: string; questions: string[] }[] = [
  {
    category: "订单查询",
    questions: ["查询我的所有订单", "查询最近一周待发货的订单", "查询订单 ORD10001 的详情"],
  },
  {
    category: "订单创建",
    questions: ["创建订单：无线键盘，数量 2，单价 99 元"],
  },
  {
    category: "订单修改",
    questions: ["把 ORD10001 的数量改为 3", "修改 ORD10002 的收货地址"],
  },
  {
    category: "订单状态",
    questions: ["取消订单 ORD10001", "将 ORD10002 标记为已发货"],
  },
  {
    category: "订单统计",
    questions: ["统计我本月的订单数量和销售额", "统计不同订单状态的数量"],
  },
];
