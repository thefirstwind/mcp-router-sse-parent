package com.pajk.mcpbridge.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器服务
 * 实现服务降级和故障转移机制
 */
@Service
public class CircuitBreakerService {

    private final static Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    // 熔断器状态缓存
    private final Map<String, CircuitBreakerState> circuitBreakerStates = new ConcurrentHashMap<>();
    
    // 默认配置
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final int DEFAULT_SUCCESS_THRESHOLD = 3;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    
    /**
     * 检查熔断器是否开启
     */
    public boolean isCircuitOpen(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.get(serviceName);
        if (state == null) {
            return false;
        }
        
        return state.isOpen();
    }
    
    /**
     * 检查熔断器是否半开
     */
    public boolean isCircuitHalfOpen(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.get(serviceName);
        if (state == null) {
            return false;
        }
        
        return state.isHalfOpen();
    }
    
    /**
     * 开启熔断器
     */
    public void openCircuit(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.computeIfAbsent(serviceName, 
                k -> new CircuitBreakerState(serviceName));
        
        state.open();
        log.warn("Circuit breaker opened for service: {}", serviceName);
    }
    
    /**
     * 关闭熔断器
     */
    public void closeCircuit(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.get(serviceName);
        if (state != null) {
            state.close();
            log.info("Circuit breaker closed for service: {}", serviceName);
        }
    }
    
    /**
     * 记录成功调用
     */
    public void recordSuccess(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.computeIfAbsent(serviceName, 
                k -> new CircuitBreakerState(serviceName));
        
        state.recordSuccess();
        
        // 检查是否应该关闭熔断器
        if (state.shouldClose()) {
            closeCircuit(serviceName);
        }
    }
    
    /**
     * 记录失败调用
     */
    public void recordFailure(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.computeIfAbsent(serviceName, 
                k -> new CircuitBreakerState(serviceName));
        
        state.recordFailure();
        
        // 检查是否应该开启熔断器
        if (state.shouldOpen()) {
            openCircuit(serviceName);
        }
    }
    
    /**
     * 尝试半开状态
     */
    public boolean attemptHalfOpen(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.get(serviceName);
        if (state == null) {
            return true;
        }
        
        return state.attemptHalfOpen();
    }
    
    /**
     * 获取熔断器状态
     */
    public CircuitBreakerState getCircuitBreakerState(String serviceName) {
        return circuitBreakerStates.get(serviceName);
    }
    
    /**
     * 获取所有熔断器状态
     */
    public Map<String, CircuitBreakerState> getAllCircuitBreakerStates() {
        return new ConcurrentHashMap<>(circuitBreakerStates);
    }
    
    /**
     * 重置熔断器
     */
    public void resetCircuitBreaker(String serviceName) {
        CircuitBreakerState state = circuitBreakerStates.get(serviceName);
        if (state != null) {
            state.reset();
            log.info("Circuit breaker reset for service: {}", serviceName);
        }
    }
    
    /**
     * 熔断器状态模型
     */
    public static class CircuitBreakerState {
        private final String serviceName;
        private volatile State state = State.CLOSED;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
        private LocalDateTime lastFailureTime;
        private LocalDateTime lastSuccessTime;
        private LocalDateTime stateChangeTime = LocalDateTime.now();
        
        // 配置参数
        private final int failureThreshold;
        private final int successThreshold;
        private final Duration timeout;
        
        public CircuitBreakerState(String serviceName) {
            this(serviceName, DEFAULT_FAILURE_THRESHOLD, DEFAULT_SUCCESS_THRESHOLD, DEFAULT_TIMEOUT);
        }
        
        public CircuitBreakerState(String serviceName, int failureThreshold, int successThreshold, Duration timeout) {
            this.serviceName = serviceName;
            this.failureThreshold = failureThreshold;
            this.successThreshold = successThreshold;
            this.timeout = timeout;
        }
        
        public void recordSuccess() {
            successCount.incrementAndGet();
            consecutiveSuccesses.incrementAndGet();
            consecutiveFailures.set(0);
            lastSuccessTime = LocalDateTime.now();
        }
        
        public void recordFailure() {
            failureCount.incrementAndGet();
            consecutiveFailures.incrementAndGet();
            consecutiveSuccesses.set(0);
            lastFailureTime = LocalDateTime.now();
        }
        
        public boolean shouldOpen() {
            return state == State.CLOSED && consecutiveFailures.get() >= failureThreshold;
        }
        
        public boolean shouldClose() {
            return state == State.HALF_OPEN && consecutiveSuccesses.get() >= successThreshold;
        }
        
        public void open() {
            state = State.OPEN;
            stateChangeTime = LocalDateTime.now();
        }
        
        public void close() {
            state = State.CLOSED;
            stateChangeTime = LocalDateTime.now();
            consecutiveFailures.set(0);
            consecutiveSuccesses.set(0);
        }
        
        public boolean attemptHalfOpen() {
            if (state == State.OPEN && 
                LocalDateTime.now().isAfter(stateChangeTime.plus(timeout))) {
                state = State.HALF_OPEN;
                stateChangeTime = LocalDateTime.now();
                return true;
            }
            return state != State.OPEN;
        }
        
        public void reset() {
            state = State.CLOSED;
            failureCount.set(0);
            successCount.set(0);
            consecutiveFailures.set(0);
            consecutiveSuccesses.set(0);
            lastFailureTime = null;
            lastSuccessTime = null;
            stateChangeTime = LocalDateTime.now();
        }
        
        public boolean isOpen() {
            return state == State.OPEN;
        }
        
        public boolean isClosed() {
            return state == State.CLOSED;
        }
        
        public boolean isHalfOpen() {
            return state == State.HALF_OPEN;
        }
        
        // Getters
        public String getServiceName() {
            return serviceName;
        }
        
        public State getState() {
            return state;
        }
        
        public int getFailureCount() {
            return failureCount.get();
        }
        
        public int getSuccessCount() {
            return successCount.get();
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }
        
        public int getConsecutiveSuccesses() {
            return consecutiveSuccesses.get();
        }
        
        public LocalDateTime getLastFailureTime() {
            return lastFailureTime;
        }
        
        public LocalDateTime getLastSuccessTime() {
            return lastSuccessTime;
        }
        
        public LocalDateTime getStateChangeTime() {
            return stateChangeTime;
        }
        
        public int getFailureThreshold() {
            return failureThreshold;
        }
        
        public int getSuccessThreshold() {
            return successThreshold;
        }
        
        public Duration getTimeout() {
            return timeout;
        }
    }
    
    /**
     * 熔断器状态枚举
     */
    public enum State {
        CLOSED,    // 关闭状态：正常运行
        OPEN,      // 开启状态：熔断器触发，拒绝调用
        HALF_OPEN  // 半开状态：尝试性调用
    }
} 