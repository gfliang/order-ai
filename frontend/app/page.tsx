"use client";

import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import {
  Alert,
  App,
  Avatar,
  Button,
  Card,
  Input,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
} from "antd";
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ReloadOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { ensureUser } from "@/lib/user";
import {
  buildContext,
  MAX_SINGLE_MESSAGE_CHARS,
  QUICK_QUESTIONS,
  streamChat,
  ToolEvent,
  toolLabel,
  trimHistory,
  ChatHistoryMessage,
} from "@/lib/ai";

const { Text, Title } = Typography;

interface ChatItem {
  id: string;
  role: "user" | "assistant";
  content: string;
  tools: ToolEvent[];
  thinking?: string;
  streaming?: boolean;
  error?: string;
  stopped?: boolean;
}

const WELCOME_CONTENT =
  "你好，我是订单管理助手，可以帮你查询、创建、修改、取消和发货订单，也可以查看订单统计。\n" +
  "识别到明确的创建、修改、取消或发货指令后，我会直接执行操作并返回真实结果。";

function createConversationId(): string {
  return `conv_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function createWelcomeItem(): ChatItem {
  return {
    id: "welcome",
    role: "assistant",
    content: WELCOME_CONTENT,
    tools: [],
  };
}

interface StoredChatState {
  ownerId: string;
  items: ChatItem[];
  history: ChatHistoryMessage[];
  conversationId: string;
  input: string;
}

/**
 * 仅保存在当前页面运行内存中（模块级变量）：
 * 路由在 AI 助手与订单管理之间切换时组件会卸载，但模块仍在内存中，可恢复上次内容；
 * 刷新页面或重新打开后模块重新加载，不再展示旧内容。
 */
let memoryChat: StoredChatState | null = null;

/** 恢复时清理中断的流式状态，避免页面停在不完整的“生成中” */
function restoreItems(items: ChatItem[]): ChatItem[] {
  return items.map((item) =>
    item.streaming || item.thinking
      ? {
          ...item,
          streaming: false,
          thinking: undefined,
          stopped: item.streaming ? true : item.stopped,
        }
      : { ...item, streaming: false },
  );
}

/** 复用已恢复的消息 id 序号，避免新旧消息 id 冲突 */
function maxItemSeq(items: ChatItem[]): number {
  return items.reduce((max, item) => {
    const matched = /^msg_(\d+)$/.exec(item.id);
    return matched ? Math.max(max, Number(matched[1])) : max;
  }, 0);
}

function toolTagColor(status: ToolEvent["status"]): string {
  switch (status) {
    case "success":
      return "green";
    case "error":
      return "red";
    default:
      return "blue";
  }
}

function toolTagIcon(status: ToolEvent["status"]) {
  switch (status) {
    case "success":
      return <CheckCircleOutlined />;
    case "error":
      return <CloseCircleOutlined />;
    default:
      return <LoadingOutlined />;
  }
}

export default function Home() {
  const { message } = App.useApp();
  const [userId, setUserId] = useState("");
  const [userError, setUserError] = useState("");
  const [items, setItems] = useState<ChatItem[]>([createWelcomeItem()]);
  const [history, setHistory] = useState<ChatHistoryMessage[]>([]);
  const [conversationId, setConversationId] = useState(createConversationId);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [restored, setRestored] = useState(false);

  const listRef = useRef<HTMLDivElement>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const streamingRef = useRef(false);
  const abortedRef = useRef(false);
  const itemSeqRef = useRef(0);
  const storedOwnerRef = useRef("");

  // 路由切换会卸载本页组件，挂载时从内存恢复上次的会话内容
  useEffect(() => {
    const stored = memoryChat;
    if (stored) {
      setItems(restoreItems(stored.items));
      setHistory(stored.history);
      setConversationId(stored.conversationId);
      setInput(stored.input);
      itemSeqRef.current = maxItemSeq(stored.items);
      storedOwnerRef.current = stored.ownerId;
    }
    setRestored(true);
  }, []);

  // 会话变化时更新内存快照，供路由往返时恢复；刷新后内存被清空
  useEffect(() => {
    if (!restored || !userId) {
      return;
    }
    memoryChat = { ownerId: userId, items, history, conversationId, input };
  }, [restored, userId, items, history, conversationId, input]);

  useEffect(() => {
    return () => {
      // 离开页面时中断未完成的流，避免组件卸载后继续请求
      abortedRef.current = true;
      controllerRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    ensureUser()
      .then((id) => {
        if (storedOwnerRef.current && storedOwnerRef.current !== id) {
          // 本地用户标识已变化，丢弃上一个用户的会话，防止串数据
          setItems([createWelcomeItem()]);
          setHistory([]);
          setConversationId(createConversationId());
          setInput("");
          itemSeqRef.current = 0;
        }
        setUserId(id);
      })
      .catch(() => setUserError("用户初始化失败，请确认后端服务已启动"));
  }, []);

  // 消息变化时自动滚动到底部
  useEffect(() => {
    const node = listRef.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [items]);

  const nextItemId = () => {
    itemSeqRef.current += 1;
    return `msg_${itemSeqRef.current}`;
  };

  const patchItem = (id: string, patch: Partial<ChatItem>) => {
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  };

  const patchTool = (id: string, event: ToolEvent) => {
    setItems((prev) =>
      prev.map((item) => {
        if (item.id !== id) {
          return item;
        }
        const exists = item.tools.some((tool) => tool.toolCallId === event.toolCallId);
        const tools = exists
          ? item.tools.map((tool) => (tool.toolCallId === event.toolCallId ? event : tool))
          : [...item.tools, event];
        return { ...item, tools };
      }),
    );
  };

  const send = async (raw: string) => {
    const text = raw.trim();
    if (!text || streamingRef.current) {
      return;
    }
    if (text.length > MAX_SINGLE_MESSAGE_CHARS) {
      message.warning(`单条消息不能超过 ${MAX_SINGLE_MESSAGE_CHARS} 字符`);
      return;
    }
    if (!userId) {
      message.warning("用户尚未初始化完成，请稍后重试");
      return;
    }

    const userItem: ChatItem = {
      id: nextItemId(),
      role: "user",
      content: text,
      tools: [],
    };
    const assistantId = nextItemId();
    const assistantItem: ChatItem = {
      id: assistantId,
      role: "assistant",
      content: "",
      tools: [],
      thinking: "正在理解你的问题...",
      streaming: true,
    };

    const context = buildContext(history, text);
    setHistory(trimHistory([...history, { role: "user", content: text }]));
    setItems((prev) => [...prev, userItem, assistantItem]);
    setInput("");

    streamingRef.current = true;
    abortedRef.current = false;
    setStreaming(true);
    const controller = new AbortController();
    controllerRef.current = controller;

    let reply = "";

    try {
      await streamChat(
        { conversationId, messages: context },
        {
          onThinking: (thinkingMessage) => {
            if (thinkingMessage) {
              patchItem(assistantId, { thinking: thinkingMessage });
            }
          },
          onMessage: (delta) => {
            reply += delta;
            setItems((prev) =>
              prev.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      content: item.content + delta,
                      thinking: undefined,
                      streaming: true,
                    }
                  : item,
              ),
            );
          },
          onToolCall: (event) => {
            patchTool(assistantId, { ...event, status: "running" });
          },
          onToolResult: (event) => {
            patchTool(assistantId, event);
          },
          onError: (code, errorMessage) => {
            patchItem(assistantId, {
              streaming: false,
              thinking: undefined,
              error: `${errorMessage}（错误码 ${code}）`,
            });
          },
          onDone: () => {
            patchItem(assistantId, {
              streaming: false,
              thinking: undefined,
            });
          },
        },
        controller.signal,
      );

      if (reply.trim()) {
        setHistory((prev) => trimHistory([...prev, { role: "assistant", content: reply }]));
      }
    } catch (err) {
      if (abortedRef.current || controller.signal.aborted) {
        patchItem(assistantId, {
          streaming: false,
          thinking: undefined,
          stopped: true,
        });
      } else {
        const errorMessage = err instanceof Error ? err.message : "AI 服务连接失败";
        patchItem(assistantId, {
          streaming: false,
          thinking: undefined,
          error: `${errorMessage}，请确认后端服务已启动后重试`,
        });
        message.error("本次 AI 回复中断，可重新发送");
      }
    } finally {
      streamingRef.current = false;
      controllerRef.current = null;
      setStreaming(false);
    }
  };

  const handleStop = () => {
    abortedRef.current = true;
    controllerRef.current?.abort();
  };

  const resetConversation = () => {
    abortedRef.current = true;
    controllerRef.current?.abort();
    setItems([createWelcomeItem()]);
    setHistory([]);
    setConversationId(createConversationId());
    setInput("");
    setStreaming(false);
    streamingRef.current = false;
    itemSeqRef.current = 0;
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void send(input);
    }
  };

  return (
    <main className="ai-page">
      <div className="ai-titlebar">
        <div className="ai-title-main">
          <Title level={4} style={{ margin: 0 }}>
            AI 智能助手
          </Title>
          <Text type="secondary" className="ai-subtitle" style={{ fontSize: 12 }}>
            基于 Qwen-Max · 所有结果均来自当前用户真实数据
            {userId ? ` · 当前用户 ${userId}` : ""}
          </Text>
        </div>
        <Space>
          <Popconfirm
            title="清空当前会话？"
            description="将同时清空对话上下文，订单数据不受影响。"
            okText="清空"
            cancelText="取消"
            onConfirm={resetConversation}
          >
            <Button size="small" icon={<ReloadOutlined />}>
              清空会话
            </Button>
          </Popconfirm>
        </Space>
      </div>

      {userError && <Alert type="error" showIcon message={userError} />}

      <Card size="small" className="ai-quick-card">
        <Space direction="vertical" size={6} style={{ width: "100%" }}>
          {QUICK_QUESTIONS.map((group) => (
            <div key={group.category} className="ai-quick-group">
              <Text type="secondary" className="ai-quick-label">
                {group.category}
              </Text>
              <Space size={[8, 8]} wrap>
                {group.questions.map((question) => (
                  <Button
                    key={question}
                    size="small"
                    shape="round"
                    disabled={streaming || !userId}
                    onClick={() => void send(question)}
                  >
                    {question}
                  </Button>
                ))}
              </Space>
            </div>
          ))}
        </Space>
      </Card>

      <Card
        className="ai-chat-card"
        styles={{
          body: {
            flex: 1,
            minHeight: 0,
            padding: 0,
            display: "flex",
            flexDirection: "column",
          },
        }}
      >
        <div ref={listRef} className="ai-message-list">
          {items.map((item) => (
            <div
              key={item.id}
              className="ai-message-row"
              style={{
                justifyContent: item.role === "user" ? "flex-end" : "flex-start",
              }}
            >
              {item.role === "assistant" && (
                <Avatar icon={<RobotOutlined />} style={{ backgroundColor: "#1677ff", flexShrink: 0 }} />
              )}
              <div
                className={`ai-bubble-col${item.role === "user" ? " ai-bubble-col--user" : ""}`}
              >
                {(item.content || item.thinking || item.streaming) && (
                  <div
                    style={{
                      padding: "10px 14px",
                      borderRadius: 10,
                      lineHeight: 1.75,
                      fontSize: 14,
                      whiteSpace: "pre-wrap",
                      wordBreak: "break-word",
                      background: item.role === "user" ? "#1677ff" : "#fff",
                      color: item.role === "user" ? "#fff" : "rgba(0,0,0,0.88)",
                      border: item.role === "user" ? "1px solid #1677ff" : "1px solid #f0f0f0",
                      boxShadow: item.role === "user" ? "none" : "0 1px 2px rgba(0,0,0,0.03)",
                    }}
                  >
                    {item.content}
                    {item.streaming && <span style={{ color: "#1677ff" }}>▍</span>}
                    {item.thinking && (item.streaming || !item.content) && (
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 8,
                          marginTop: item.content ? 8 : 0,
                          color: "rgba(0,0,0,0.45)",
                          fontSize: 13,
                        }}
                      >
                        <Spin size="small" />
                        <span>{item.thinking}</span>
                      </div>
                    )}
                  </div>
                )}

                {item.tools.length > 0 && (
                  <Space size={[6, 6]} wrap style={{ maxWidth: "100%" }}>
                    {item.tools.map((tool) => (
                      <Tag
                        key={tool.toolCallId || `${tool.name}-${tool.status}`}
                        color={toolTagColor(tool.status)}
                        style={{ marginInlineEnd: 0 }}
                      >
                        {toolTagIcon(tool.status)}
                        {`${toolLabel(tool.name)}${
                          tool.status === "running"
                            ? " 执行中"
                            : tool.summary
                              ? ` · ${tool.summary}`
                              : ""
                        }`}
                      </Tag>
                    ))}
                  </Space>
                )}

                {item.error && (
                  <Alert
                    type="error"
                    showIcon
                    message="本次 AI 回复中断，可重新发送"
                    description={item.error}
                    style={{ width: "100%" }}
                  />
                )}

                {item.stopped && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    已停止生成，可重新发送问题
                  </Text>
                )}
              </div>
              {item.role === "user" && (
                <Avatar icon={<UserOutlined />} style={{ backgroundColor: "#52c41a", flexShrink: 0 }} />
              )}
            </div>
          ))}
        </div>

        <div className="ai-input-panel">
          <Input.TextArea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="描述你的订单需求，例如：查询我最近一周待发货的订单"
            autoSize={{ minRows: 1, maxRows: 4 }}
            maxLength={MAX_SINGLE_MESSAGE_CHARS}
            disabled={!userId && !userError}
            style={{ resize: "none" }}
          />
          <div className="ai-input-footer">
            <Text type="secondary" className="ai-input-hint" style={{ fontSize: 12 }}>
              Enter 发送 · Shift + Enter 换行
            </Text>
            <Space>
              {streaming && (
                <Button size="small" danger icon={<StopOutlined />} onClick={handleStop}>
                  停止生成
                </Button>
              )}
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={streaming}
                disabled={streaming || !input.trim() || !userId}
                onClick={() => void send(input)}
              >
                发送
              </Button>
            </Space>
          </div>
        </div>
      </Card>
    </main>
  );
}
