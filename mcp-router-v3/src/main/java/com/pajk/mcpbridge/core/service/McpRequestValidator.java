package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.model.McpMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP 请求验证器
 * 验证 MCP 消息的格式和内容
 */
@Slf4j
@Component
public class McpRequestValidator {

    /**
     * 验证 MCP 请求
     * @param message MCP 消息
     * @return 如果验证通过返回 null，否则返回错误消息
     */
    public String validateRequest(McpMessage message) {
        if (message == null) {
            return "Message cannot be null";
        }

        // 验证 JSON-RPC 版本
        if (message.getJsonrpc() == null || !"2.0".equals(message.getJsonrpc())) {
            return "Invalid JSON-RPC version, must be 2.0";
        }

        // 验证方法名（对于请求消息）
        if (message.getMethod() != null && message.getMethod().isEmpty()) {
            return "Method cannot be empty if provided";
        }

        // 验证 ID（对于请求和响应消息）
        // 注意：通知消息可能没有 ID，这是合法的
        // 但响应消息必须有 ID

        return null; // 验证通过
    }
}
