package com.nacos.mcp.router.v3.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 负载均衡器
 * 支持多种负载均衡策略
 */
@Slf4j
@Component
public class LoadBalancer {
    
    /**
     * 负载均衡策略
     */
    public enum Strategy {
        ROUND_ROBIN,    // 轮询
        RANDOM,         // 随机
        WEIGHTED_ROUND_ROBIN,  // 加权轮询
        LEAST_CONNECTIONS      // 最少连接
    }
    
    // 轮询计数器
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    // 默认策略
    private Strategy defaultStrategy = Strategy.ROUND_ROBIN;
    
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
        
        return switch (strategy) {
            case ROUND_ROBIN -> selectByRoundRobin(healthyInstances);
            case RANDOM -> selectByRandom(healthyInstances);
            case WEIGHTED_ROUND_ROBIN -> selectByWeightedRoundRobin(healthyInstances);
            case LEAST_CONNECTIONS -> selectByLeastConnections(healthyInstances);
        };
    }
    
    /**
     * 轮询算法
     */
    private Instance selectByRoundRobin(List<Instance> instances) {
        int index = roundRobinIndex.getAndIncrement() % instances.size();
        Instance selected = instances.get(index);
        log.debug("Selected instance by round-robin: {}:{}", selected.getIp(), selected.getPort());
        return selected;
    }
    
    /**
     * 随机算法
     */
    private Instance selectByRandom(List<Instance> instances) {
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        Instance selected = instances.get(index);
        log.debug("Selected instance by random: {}:{}", selected.getIp(), selected.getPort());
        return selected;
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
        
        // 生成随机数
        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        
        // 根据权重选择
        double currentWeight = 0;
        for (Instance instance : instances) {
            currentWeight += instance.getWeight();
            if (random <= currentWeight) {
                log.debug("Selected instance by weighted round-robin: {}:{} (weight: {})", 
                        instance.getIp(), instance.getPort(), instance.getWeight());
                return instance;
            }
        }
        
        // 默认返回第一个
        return instances.get(0);
    }
    
    /**
     * 最少连接算法
     * 注意：这里简化实现，实际应该基于连接数统计
     */
    private Instance selectByLeastConnections(List<Instance> instances) {
        // 简化实现：选择权重最高的实例（假设权重反映连接负载）
        Instance selected = instances.stream()
                .max((i1, i2) -> Double.compare(i1.getWeight(), i2.getWeight()))
                .orElse(instances.get(0));
        
        log.debug("Selected instance by least connections: {}:{}", selected.getIp(), selected.getPort());
        return selected;
    }
    
    /**
     * 设置默认策略
     */
    public void setDefaultStrategy(Strategy strategy) {
        this.defaultStrategy = strategy;
        log.info("Load balancer default strategy changed to: {}", strategy);
    }
    
    /**
     * 获取当前默认策略
     */
    public Strategy getDefaultStrategy() {
        return defaultStrategy;
    }
} 