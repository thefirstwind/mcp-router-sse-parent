# MCP Bridge v3 配置参考手册

## 📋 概述

本手册详细说明了 MCP Bridge v3 的所有配置选项，包括核心配置、Nacos 集成配置、性能调优配置等。

---

## 🏗️ 配置文件结构

### 主配置文件 (application.yml)

```yaml
# ==========================================
# MCP Bridge v3 完整配置示例
# ==========================================

# 应用基础配置
spring:
  application:
    name: mcp-bridge-v3
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  # AI 框架配置
  ai:
    alibaba:
      mcp:
        # Nacos 连接配置
        nacos:
          server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
          namespace: ${NACOS_NAMESPACE:public}
          username: ${NACOS_USERNAME:nacos}
          password: ${NACOS_PASSWORD:nacos}
          access-key: ${NACOS_ACCESS_KEY:}
          secret-key: ${NACOS_SECRET_KEY:}
          endpoint: ${NACOS_ENDPOINT:}
          ip: ${SERVICE_IP:}
          
          # 服务注册配置
          registry:
            enabled: true
            service-group: mcp-bridge
            service-name: ${spring.application.name}
            service-register: true
            service-ephemeral: true
            
        # Bridge 核心配置
        bridge:
          # 路由配置
          routing:
            strategy: SMART_ROUTING  # DIRECT, SMART_ROUTING, LOAD_BALANCED
            timeout: 30s
            retry:
              enabled: true
              max-attempts: 3
              backoff-delay: 1s
              
          # 负载均衡配置
          load-balancer:
            algorithm: WEIGHTED_ROUND_ROBIN  # ROUND_ROBIN, RANDOM, LEAST_CONNECTIONS
            health-check:
              enabled: true
              interval: 30s
              timeout: 5s
              failure-threshold: 3
              recovery-threshold: 2
              
          # 连接池配置
          connection-pool:
            max-connections: 50
            min-idle-connections: 5
            connection-timeout: 30s
            idle-timeout: 300s
            max-lifetime: 1800s
            leak-detection-threshold: 60s
            
          # 缓存配置
          cache:
            enabled: true
            tool-results:
              enabled: true
              ttl: 300s
              max-size: 1000
            service-discovery:
              enabled: true
              ttl: 60s
              max-size: 100

# 服务器配置
server:
  port: ${SERVER_PORT:8080}
  address: ${SERVER_ADDRESS:0.0.0.0}
  
  # Tomcat 配置 (如果使用 Tomcat)
  tomcat:
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20s
    max-connections: 8192
    
  # Netty 配置 (如果使用 WebFlux)
  netty:
    connection-timeout: 20s
    h2c-max-content-length: 0
    initial-buffer-size: 128
    max-chunk-size: 8192
    max-initial-line-length: 4096
    validate-headers: true

# 管理端点配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,mcp-bridge
      base-path: /actuator
      cors:
        allowed-origins: "*"
        allowed-methods: GET,POST
        
  endpoint:
    health:
      show-details: when-authorized
      show-components: always
    mcp-bridge:
      enabled: true
      
  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s
        descriptions: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
      
  health:
    nacos:
      enabled: true
    mcp-bridge:
      enabled: true

# 日志配置
logging:
  level:
    root: INFO
    com.nacos.mcp.bridge: DEBUG
    com.alibaba.nacos: WARN
    io.modelcontextprotocol: DEBUG
    org.springframework.ai: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/mcp-bridge-v3.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 1GB
```

---

## 🔧 配置详解

### 1. Nacos 配置 (`spring.ai.alibaba.mcp.nacos`)

#### 1.1 基础连接配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `server-addr` | String | `127.0.0.1:8848` | Nacos 服务器地址 |
| `namespace` | String | `public` | 命名空间 ID |
| `username` | String | - | 认证用户名 |
| `password` | String | - | 认证密码 |
| `access-key` | String | - | 阿里云 AccessKey |
| `secret-key` | String | - | 阿里云 SecretKey |
| `endpoint` | String | - | 阿里云 Endpoint |
| `ip` | String | 自动检测 | 服务注册 IP |

**示例配置**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          # 基础配置
          server-addr: "nacos-cluster:8848"
          namespace: "mcp-production"
          
          # 认证配置 (可选)
          username: "mcp-user"
          password: "mcp-password"
          
          # 阿里云 MSE 配置 (可选)
          access-key: "${ALIBABA_ACCESS_KEY}"
          secret-key: "${ALIBABA_SECRET_KEY}"
          endpoint: "mse.aliyuncs.com"
```

#### 1.2 服务注册配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `registry.enabled` | Boolean | `true` | 是否启用服务注册 |
| `registry.service-group` | String | `DEFAULT_GROUP` | 服务分组 |
| `registry.service-name` | String | `${spring.application.name}` | 服务名称 |
| `registry.service-register` | Boolean | `true` | 是否注册服务实例 |
| `registry.service-ephemeral` | Boolean | `true` | 是否为临时实例 |

### 2. Bridge 核心配置 (`spring.ai.alibaba.mcp.bridge`)

#### 2.1 路由策略配置

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          routing:
            # 路由策略
            strategy: SMART_ROUTING
            
            # 请求超时
            timeout: 30s
            
            # 重试配置
            retry:
              enabled: true
              max-attempts: 3
              backoff-delay: 1s
              backoff-multiplier: 2.0
              max-backoff-delay: 10s
              jitter: 0.1
              
            # 并发控制
            circuit-breaker:
              enabled: true
              failure-rate-threshold: 50
              slow-call-rate-threshold: 50
              slow-call-duration-threshold: 10s
              minimum-number-of-calls: 10
              sliding-window-size: 20
```

**路由策略说明**:
- `DIRECT`: 直接路由，不经过负载均衡
- `SMART_ROUTING`: 智能路由，根据工具名称自动发现服务
- `LOAD_BALANCED`: 负载均衡路由，在多个实例间分配请求

#### 2.2 负载均衡配置

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          load-balancer:
            # 负载均衡算法
            algorithm: WEIGHTED_ROUND_ROBIN
            
            # 权重配置
            weights:
              mcp-server-v6: 3
              mcp-server-v3: 1
              
            # 健康检查
            health-check:
              enabled: true
              interval: 30s
              timeout: 5s
              failure-threshold: 3
              recovery-threshold: 2
              
              # 自定义健康检查端点
              endpoints:
                - path: "/actuator/health"
                  method: GET
                  expected-status: 200
```

**负载均衡算法**:
- `ROUND_ROBIN`: 轮询
- `WEIGHTED_ROUND_ROBIN`: 加权轮询
- `RANDOM`: 随机
- `LEAST_CONNECTIONS`: 最少连接

#### 2.3 连接池配置

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          connection-pool:
            # 连接数配置
            max-connections: 50
            min-idle-connections: 5
            max-connections-per-route: 10
            
            # 超时配置
            connection-timeout: 30s
            socket-timeout: 60s
            connection-request-timeout: 10s
            
            # 生命周期配置
            idle-timeout: 300s
            max-lifetime: 1800s
            keep-alive-duration: 30s
            
            # 监控配置
            leak-detection-threshold: 60s
            validation-query-timeout: 5s
            test-on-borrow: true
            test-on-return: false
            test-while-idle: true
```

#### 2.4 缓存配置

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          cache:
            enabled: true
            
            # 工具结果缓存
            tool-results:
              enabled: true
              ttl: 300s
              max-size: 1000
              key-prefix: "tool_result:"
              
            # 服务发现缓存
            service-discovery:
              enabled: true
              ttl: 60s
              max-size: 100
              key-prefix: "service:"
              
            # 工具定义缓存
            tool-definitions:
              enabled: true
              ttl: 3600s
              max-size: 500
              
            # 缓存提供者
            provider: CAFFEINE  # CAFFEINE, REDIS, HAZELCAST
```

---

## 🌍 环境配置

### 开发环境 (application-dev.yml)

```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: 127.0.0.1:8848
          namespace: dev
        bridge:
          routing:
            timeout: 10s
          connection-pool:
            max-connections: 10
          cache:
            tool-results:
              ttl: 60s

logging:
  level:
    com.nacos.mcp.bridge: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

### 测试环境 (application-test.yml)

```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: nacos-test.internal:8848
          namespace: test
        bridge:
          routing:
            timeout: 20s
          connection-pool:
            max-connections: 25
          load-balancer:
            health-check:
              interval: 15s

logging:
  level:
    root: WARN
    com.nacos.mcp.bridge: INFO
```

### 生产环境 (application-prod.yml)

```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: ${NACOS_CLUSTER_ADDR}
          namespace: production
          username: ${NACOS_USERNAME}
          password: ${NACOS_PASSWORD}
        bridge:
          routing:
            timeout: 30s
            retry:
              max-attempts: 5
          connection-pool:
            max-connections: 100
            min-idle-connections: 10
          load-balancer:
            health-check:
              failure-threshold: 5
          cache:
            enabled: true
            provider: REDIS

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
        
security:
  api:
    enabled: true
    key: ${API_KEY}
```

---

## 🚀 性能调优配置

### 1. JVM 参数优化

```bash
# 生产环境 JVM 参数
JAVA_OPTS="-Xms2g -Xmx4g \
           -XX:+UseG1GC \
           -XX:MaxGCPauseMillis=200 \
           -XX:+UseStringDeduplication \
           -XX:+OptimizeStringConcat \
           -Djava.awt.headless=true \
           -Dfile.encoding=UTF-8 \
           -Duser.timezone=Asia/Shanghai"

# 开发环境 JVM 参数
JAVA_OPTS="-Xms512m -Xmx1g \
           -XX:+UseG1GC \
           -Dspring.profiles.active=dev"
```

### 2. 高并发配置

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          # 高并发路由配置
          routing:
            timeout: 15s
            parallel-processing: true
            max-concurrent-requests: 1000
            
          # 高性能连接池
          connection-pool:
            max-connections: 200
            min-idle-connections: 20
            max-connections-per-route: 50
            connection-timeout: 10s
            socket-timeout: 30s
            
          # 缓存优化
          cache:
            provider: REDIS
            tool-results:
              ttl: 600s
              max-size: 10000

# 服务器优化
server:
  tomcat:
    threads:
      max: 500
      min-spare: 50
    max-connections: 20000
    accept-count: 1000
    connection-timeout: 10s
```

### 3. 内存优化配置

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          # 内存敏感配置
          cache:
            tool-results:
              max-size: 5000
              eviction-policy: LRU
            service-discovery:
              max-size: 200
              
          connection-pool:
            # 减少连接数以节省内存
            max-connections: 50
            idle-timeout: 180s
            
          # 批处理优化
          batch-processing:
            enabled: true
            batch-size: 100
            flush-interval: 1s
```

---

## 🔒 安全配置

### 1. API 认证配置

```yaml
spring:
  security:
    api:
      enabled: true
      # API Key 认证
      keys:
        - key: "api-key-001"
          name: "web-app"
          permissions: ["READ", "WRITE"]
        - key: "api-key-002"
          name: "admin-tool"
          permissions: ["READ", "WRITE", "ADMIN"]
          
      # JWT 认证
      jwt:
        enabled: true
        secret: "${JWT_SECRET}"
        expiration: 3600s
        
      # IP 白名单
      ip-whitelist:
        enabled: true
        addresses:
          - "192.168.1.0/24"
          - "10.0.0.0/8"
```

### 2. TLS/SSL 配置

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: "${KEYSTORE_PASSWORD}"
    key-store-type: PKCS12
    key-alias: mcp-bridge
    
  # 强制 HTTPS
  require-ssl: true
```

---

## 📊 监控配置

### 1. Metrics 配置

```yaml
management:
  metrics:
    tags:
      application: mcp-bridge-v3
      environment: ${spring.profiles.active}
      version: ${project.version}
      
    export:
      # Prometheus 配置
      prometheus:
        enabled: true
        step: 30s
        descriptions: true
        
      # InfluxDB 配置 (可选)
      influx:
        enabled: false
        uri: http://localhost:8086
        db: mcp-bridge
        
    # 自定义 metrics
    enable:
      jvm: true
      system: true
      web: true
      mcp-bridge: true
```

### 2. 健康检查配置

```yaml
management:
  health:
    # 自定义健康指示器
    mcp-bridge:
      enabled: true
      
    nacos:
      enabled: true
      
    # 健康检查详情
    show-details: when-authorized
    show-components: always
    
    # 健康检查组
    group:
      readiness:
        include: nacos,mcp-bridge
        show-details: always
      liveness:
        include: ping
        show-details: never
```

---

## 🐛 调试配置

### 开发调试配置

```yaml
logging:
  level:
    # Bridge 相关日志
    com.nacos.mcp.bridge: DEBUG
    com.nacos.mcp.bridge.service: TRACE
    
    # Nacos 相关日志
    com.alibaba.nacos: INFO
    
    # Spring AI 相关日志
    org.springframework.ai: DEBUG
    
    # MCP 协议日志
    io.modelcontextprotocol: DEBUG
    
    # HTTP 请求日志
    org.springframework.web: DEBUG
    reactor.netty.http: DEBUG
    
  # 输出配置
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n%wEx"
    
debug: true

spring:
  output:
    ansi:
      enabled: always
```

---

## 📋 配置验证

### 配置验证规则

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          # 配置验证
          validation:
            enabled: true
            
            # 必需配置检查
            required-properties:
              - "spring.ai.alibaba.mcp.nacos.server-addr"
              - "spring.application.name"
              
            # 配置范围验证
            constraints:
              connection-pool.max-connections:
                min: 1
                max: 1000
              routing.timeout:
                min: 1s
                max: 300s
```

### 配置热更新

```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: true
        server-addr: ${spring.ai.alibaba.mcp.nacos.server-addr}
        file-extension: yaml
        namespace: ${spring.ai.alibaba.mcp.nacos.namespace}
        
        # 配置自动刷新
        refresh-enabled: true
        
        # 配置监听
        extension-configs:
          - data-id: mcp-bridge-dynamic.yaml
            group: ${spring.ai.alibaba.mcp.nacos.registry.service-group}
            refresh: true
```

---

## 🔄 配置迁移指南

### 从 v2.x 迁移到 v3.x

#### 1. 配置结构变更

**v2.x 配置**:
```yaml
mcp:
  router:
    nacos:
      server-addr: 127.0.0.1:8848
```

**v3.x 配置**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: 127.0.0.1:8848
```

#### 2. 迁移脚本

```bash
#!/bin/bash
# 配置迁移脚本

# 备份旧配置
cp application.yml application-v2-backup.yml

# 转换配置格式
sed -i 's/mcp.router/spring.ai.alibaba.mcp.bridge/g' application.yml
sed -i 's/mcp.nacos/spring.ai.alibaba.mcp.nacos/g' application.yml

echo "配置迁移完成，请检查 application.yml"
```

---

## 📝 配置模板

### 最小配置模板

```yaml
spring:
  application:
    name: mcp-bridge-v3
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: 127.0.0.1:8848

server:
  port: 8080
```

### 生产配置模板

```yaml
spring:
  application:
    name: mcp-bridge-v3
  profiles:
    active: prod
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: ${NACOS_CLUSTER}
          namespace: production
          username: ${NACOS_USER}
          password: ${NACOS_PASS}
        bridge:
          routing:
            timeout: 30s
            retry:
              enabled: true
              max-attempts: 3
          connection-pool:
            max-connections: 100
          load-balancer:
            algorithm: WEIGHTED_ROUND_ROBIN
          cache:
            enabled: true

server:
  port: ${SERVER_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

logging:
  level:
    com.nacos.mcp.bridge: INFO
  file:
    name: logs/mcp-bridge.log
```

---

> 💡 **配置建议**: 
> 1. 开发环境优先考虑调试便利性
> 2. 测试环境接近生产配置
> 3. 生产环境重点关注性能和稳定性
> 4. 定期检查和优化配置参数



