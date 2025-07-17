package com.nacos.mcp.router.v3.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE会话信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseSession {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 客户端ID
     */
    private String clientId;
    
    /**
     * 会话状态
     */
    private SessionStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;
    
    /**
     * 超时时间（毫秒）
     */
    private long timeoutMs;
    
    /**
     * 消息发送器
     */
    private Sinks.Many<ServerSentEvent<String>> sink;
    
    /**
     * 消息计数器
     */
    private AtomicLong messageCount;
    
    /**
     * 错误计数器
     */
    private AtomicLong errorCount;
    
    /**
     * 会话元数据
     */
    private java.util.Map<String, String> metadata;
    
    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR,
        TIMEOUT
    }
    
    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }
    
    /**
     * 增加消息计数
     */
    public void incrementMessageCount() {
        if (this.messageCount == null) {
            this.messageCount = new AtomicLong(0);
        }
        this.messageCount.incrementAndGet();
    }
    
    /**
     * 增加错误计数
     */
    public void incrementErrorCount() {
        if (this.errorCount == null) {
            this.errorCount = new AtomicLong(0);
        }
        this.errorCount.incrementAndGet();
    }
    
    /**
     * 检查会话是否超时
     */
    public boolean isTimeout() {
        if (lastActiveTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(lastActiveTime.plusNanos(timeoutMs * 1_000_000));
    }
} 