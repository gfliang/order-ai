package com.example.orderai.ai.client;

/** SSE 客户端已断开或事件发送失败，用于终止模型流读取 */
public class StreamCancelledException extends RuntimeException {

    public StreamCancelledException() {
        super("SSE 客户端已断开");
    }
}
