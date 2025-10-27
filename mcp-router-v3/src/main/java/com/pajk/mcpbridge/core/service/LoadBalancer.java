package com.pajk.mcpbridge.core.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的负载均衡器
 * 支持多种负载均衡策略，包括基于响应时间和负载的智能路由
 */
@Component
public class LoadBalancer {

    private final static Logger log = LoggerFactory.getLogger(LoadBalancer.class);

    /**
     * 负载均衡策略
     */
    public enum Strategy {
        ROUND_ROBIN,          // 轮询
        RANDOM,               // 随机
        WEIGHTED_ROUND_ROBIN, // 加权轮询
        LEAST_CONNECTIONS,    // 最少连接
        FASTEST_RESPONSE,     // 最快响应时间
        ADAPTIVE_LOAD,        // 自适应负载
        SMART_ROUTING        // 智能路由（综合多因素）
    }
    
    // 轮询计数器
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    // 默认策略
    private Strategy defaultStrategy = Strategy.SMART_ROUTING;
    
    // 服务器性能统计
    private final Map<String, ServerMetrics> serverMetrics = new ConcurrentHashMap<>();
    
    // 连接计数器
    private final Map<String, AtomicLong> connectionCounts = new ConcurrentHashMap<>();
    
    /**
     * 选择服务实例 - 使用默认策略
     */
    public Instance selectServer(List<Instance> instances) {
        return selectServer(instances, defaultStrategy);
    }
    
    /**
     * 选择服务实例 - 指定策略
     */
    public Instance selectServer(List<Instance> instances, Strategy strategy) {
        if (instances == null || instances.isEmpty()) {
            log.warn("No instances available for load balancing");
            return null;
        }
        
        // 过滤健康的实例
        List<Instance> healthyInstances = instances.stream()
                .filter(Instance::isHealthy)
                .filter(Instance::isEnabled)
                .toList();
        
        if (healthyInstances.isEmpty()) {
            log.warn("No healthy instances available for load balancing");
            return null;
        }
        
        if (healthyInstances.size() == 1) {
            return healthyInstances.get(0);
        }
        
        Instance selected = switch (strategy) {
            case ROUND_ROBIN -> selectByRoundRobin(healthyInstances);
            case RANDOM -> selectByRandom(healthyInstances);
            case WEIGHTED_ROUND_ROBIN -> selectByWeightedRoundRobin(healthyInstances);
            case LEAST_CONNECTIONS -> selectByLeastConnections(healthyInstances);
            case FASTEST_RESPONSE -> selectByFastestResponse(healthyInstances);
            case ADAPTIVE_LOAD -> selectByAdaptiveLoad(healthyInstances);
            case SMART_ROUTING -> selectBySmartRouting(healthyInstances);
        };
        
        if (selected != null) {
            // 增加连接计数
            String serverKey = buildServerKey(selected);
            connectionCounts.computeIfAbsent(serverKey, k -> new AtomicLong(0)).incrementAndGet();
            
            log.debug("Selected instance by {}: {}:{} (strategy: {})", 
                    strategy, selected.getIp(), selected.getPort(), strategy);
        }
        
        return selected;
    }
    
    /**
     * 轮询算法
     */
    private Instance selectByRoundRobin(List<Instance> instances) {
        int index = roundRobinIndex.getAndIncrement() % instances.size();
        return instances.get(index);
    }
    
    /**
     * 随机算法
     */
    private Instance selectByRandom(List<Instance> instances) {
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        return instances.get(index);
    }
    
    /**
     * 加权轮询算法
     */
    private Instance selectByWeightedRoundRobin(List<Instance> instances) {
        // 计算总权重
        double totalWeight = instances.stream()
                .mapToDouble(Instance::getWeight)
                .sum();
        
        if (totalWeight <= 0) {
            return selectByRoundRobin(instances);
        }
        
        // 生成随机权重值
        double randomWeight = ThreadLocalRandom.current().nextDouble(totalWeight);
        double currentWeight = 0;
        
        for (Instance instance : instances) {
            currentWeight += instance.getWeight();
            if (randomWeight <= currentWeight) {
                return instance;
            }
        }
        
        return instances.get(instances.size() - 1);
    }
    
    /**
     * 最少连接算法
     */
    private Instance selectByLeastConnections(List<Instance> instances) {
        return instances.stream()
                .min(Comparator.comparingLong(instance -> {
                    String serverKey = buildServerKey(instance);
                    return connectionCounts.getOrDefault(serverKey, new AtomicLong(0)).get();
                }))
                .orElse(instances.get(0));
    }
    
    /**
     * 最快响应时间算法
     */
    private Instance selectByFastestResponse(List<Instance> instances) {
        return instances.stream()
                .min(Comparator.comparingLong(instance -> {
                    String serverKey = buildServerKey(instance);
                    ServerMetrics metrics = serverMetrics.get(serverKey);
                    return metrics != null ? metrics.getAverageResponseTime() : Long.MAX_VALUE;
                }))
                .orElse(instances.get(0));
    }
    
    /**
     * 自适应负载算法
     */
    private Instance selectByAdaptiveLoad(List<Instance> instances) {
        return instances.stream()
                .min(Comparator.comparingDouble(instance -> {
                    String serverKey = buildServerKey(instance);
                    ServerMetrics metrics = serverMetrics.get(serverKey);
                    if (metrics == null) {
                        return 0.0; // 新服务器优先级最高
                    }
                    
                    // 综合考虑响应时间、连接数和错误率
                    double responseTimeFactor = metrics.getAverageResponseTime() / 1000.0; // 转换为秒
                    double connectionFactor = connectionCounts.getOrDefault(serverKey, new AtomicLong(0)).get();
                    double errorRateFactor = metrics.getErrorRate() * 10; // 放大错误率影响
                    
                    return responseTimeFactor + connectionFactor * 0.1 + errorRateFactor;
                }))
                .orElse(instances.get(0));
    }
    
    /**
     * 智能路由算法（综合多因素）
     */
    private Instance selectBySmartRouting(List<Instance> instances) {
        // 如果只有一个实例，直接返回
        if (instances.size() == 1) {
            return instances.get(0);
        }
        
        // 计算每个实例的综合评分
        return instances.stream()
                .min(Comparator.comparingDouble(this::calculateInstanceScore))
                .orElse(instances.get(0));
    }
    
    /**
     * 计算实例综合评分（分数越低越好）
     */
    private double calculateInstanceScore(Instance instance) {
        String serverKey = buildServerKey(instance);
        ServerMetrics metrics = serverMetrics.get(serverKey);
        
        // 基础权重（Nacos配置的权重，权重越高分数越低）
        double weightScore = instance.getWeight() > 0 ? 1.0 / instance.getWeight() : 1.0;
        
        if (metrics == null) {
            // 新服务器给予中等优先级
            return weightScore * 0.5;
        }
        
        // 响应时间评分（毫秒转换为分数）
        double responseTimeScore = Math.min(metrics.getAverageResponseTime() / 1000.0, 10.0);
        
        // 连接数评分
        long connections = connectionCounts.getOrDefault(serverKey, new AtomicLong(0)).get();
        double connectionScore = Math.min(connections * 0.1, 5.0);
        
        // 错误率评分
        double errorScore = metrics.getErrorRate() * 20;
        
        // 负载评分（基于最近的请求频率）
        double loadScore = Math.min(metrics.getRequestsPerSecond() / 10.0, 3.0);
        
        // 健康度评分（基于最近的健康检查结果）
        double healthScore = metrics.getHealthScore() > 0.8 ? 0 : (1 - metrics.getHealthScore()) * 5;
        
        // 综合评分（各因素权重可调整）
        double totalScore = weightScore * 0.2 + 
                           responseTimeScore * 0.3 + 
                           connectionScore * 0.2 + 
                           errorScore * 0.1 + 
                           loadScore * 0.1 + 
                           healthScore * 0.1;
        
        log.debug("Smart routing score for {}:{} = {:.3f} (rt:{:.3f}, conn:{:.3f}, err:{:.3f}, load:{:.3f}, health:{:.3f})",
                instance.getIp(), instance.getPort(), totalScore,
                responseTimeScore, connectionScore, errorScore, loadScore, healthScore);
        
        return totalScore;
    }
    
    /**
     * 记录请求响应时间
     */
    public void recordResponseTime(Instance instance, long responseTimeMs) {
        String serverKey = buildServerKey(instance);
        serverMetrics.computeIfAbsent(serverKey, k -> new ServerMetrics())
                .recordResponseTime(responseTimeMs);
    }
    
    /**
     * 记录请求错误
     */
    public void recordError(Instance instance) {
        String serverKey = buildServerKey(instance);
        serverMetrics.computeIfAbsent(serverKey, k -> new ServerMetrics())
                .recordError();
    }
    
    /**
     * 记录请求成功
     */
    public void recordSuccess(Instance instance) {
        String serverKey = buildServerKey(instance);
        serverMetrics.computeIfAbsent(serverKey, k -> new ServerMetrics())
                .recordSuccess();
    }
    
    /**
     * 减少连接计数
     */
    public void decrementConnectionCount(Instance instance) {
        String serverKey = buildServerKey(instance);
        AtomicLong count = connectionCounts.get(serverKey);
        if (count != null) {
            count.decrementAndGet();
        }
    }
    
    /**
     * 更新健康度评分
     */
    public void updateHealthScore(Instance instance, double healthScore) {
        String serverKey = buildServerKey(instance);
        serverMetrics.computeIfAbsent(serverKey, k -> new ServerMetrics())
                .updateHealthScore(healthScore);
    }
    
    /**
     * 获取负载均衡统计信息
     */
    public Map<String, Object> getLoadBalancerStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("strategy", defaultStrategy.toString());
        stats.put("total_servers", serverMetrics.size());
        stats.put("server_metrics", serverMetrics);
        stats.put("connection_counts", connectionCounts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get()
                )));
        return stats;
    }
    
    /**
     * 构建服务器键
     */
    private String buildServerKey(Instance instance) {
        return instance.getIp() + ":" + instance.getPort();
    }
    
    /**
     * 服务器性能指标类
     */
    public static class ServerMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong errorRequests = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private volatile double healthScore = 1.0;
        private volatile LocalDateTime lastRequestTime = LocalDateTime.now();
        
        public void recordResponseTime(long responseTimeMs) {
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);
            lastRequestTime = LocalDateTime.now();
        }
        
        public void recordSuccess() {
            successfulRequests.incrementAndGet();
            lastRequestTime = LocalDateTime.now();
        }
        
        public void recordError() {
            errorRequests.incrementAndGet();
            lastRequestTime = LocalDateTime.now();
        }
        
        public void updateHealthScore(double score) {
            this.healthScore = score;
        }
        
        public long getAverageResponseTime() {
            long total = totalRequests.get();
            return total > 0 ? totalResponseTime.get() / total : 0;
        }
        
        public double getErrorRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) errorRequests.get() / total : 0.0;
        }
        
        public double getSuccessRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) successfulRequests.get() / total : 1.0;
        }
        
        public double getRequestsPerSecond() {
            LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
            if (lastRequestTime.isAfter(oneMinuteAgo)) {
                long secondsSinceLastRequest = Duration.between(lastRequestTime, LocalDateTime.now()).getSeconds();
                return secondsSinceLastRequest > 0 ? totalRequests.get() / (double) secondsSinceLastRequest : 0.0;
            }
            return 0.0;
        }
        
        public double getHealthScore() {
            return healthScore;
        }
        
        // Getters for monitoring
        public long getTotalRequests() { return totalRequests.get(); }
        public long getSuccessfulRequests() { return successfulRequests.get(); }
        public long getErrorRequests() { return errorRequests.get(); }
        public LocalDateTime getLastRequestTime() { return lastRequestTime; }
    }
} 