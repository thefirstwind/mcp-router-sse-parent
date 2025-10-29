# MCP Bridge v3 故障排查指南

## 📋 概述

本指南提供了 MCP Bridge v3 常见问题的诊断方法和解决方案，帮助运维人员和开发者快速定位和解决问题。

---

## 🚨 快速诊断清单

### 系统健康检查
```bash
# 1. 检查服务状态
curl http://localhost:8080/actuator/health

# 2. 检查 Nacos 连接
curl http://localhost:8080/actuator/health/nacos

# 3. 检查注册服务
curl http://localhost:8080/mcp/bridge/services

# 4. 检查系统统计
curl http://localhost:8080/mcp/bridge/stats

# 5. 检查日志
tail -f logs/mcp-bridge-v3.log
```

---

## 🔍 常见问题分类排查

### 1. 启动问题

#### 1.1 服务启动失败

**问题现象**:
```
Application failed to start
Description: Failed to configure a DataSource
```

**可能原因**:
- 配置文件错误
- 依赖缺失
- 端口冲突
- JVM 内存不足

**排查步骤**:
```bash
# 检查配置文件
./gradlew bootRun --debug

# 检查端口占用
netstat -tlnp | grep 8080
lsof -i :8080

# 检查 JVM 内存
java -XX:+PrintFlagsFinal -version | grep MaxHeapSize

# 查看详细启动日志
java -jar mcp-bridge-v3.jar --debug
```

**解决方案**:
```yaml
# 修正配置文件
spring:
  application:
    name: mcp-bridge-v3
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: 127.0.0.1:8848  # 确保 Nacos 地址正确

# 更换端口
server:
  port: 8081

# 增加内存
JAVA_OPTS="-Xms1g -Xmx2g"
```

#### 1.2 Nacos 连接失败

**问题现象**:
```
com.alibaba.nacos.api.exception.NacosException: 
failed to req API:/nacos/v1/ns/instance after all servers tried
```

**排查步骤**:
```bash
# 1. 检查 Nacos 服务状态
curl http://127.0.0.1:8848/nacos/v1/console/health/liveness

# 2. 检查网络连通性
ping 127.0.0.1
telnet 127.0.0.1 8848

# 3. 检查 Nacos 认证
curl -X POST 'http://127.0.0.1:8848/nacos/v1/auth/login' \
  -d 'username=nacos&password=nacos'

# 4. 检查防火墙
iptables -L | grep 8848
```

**解决方案**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          server-addr: 127.0.0.1:8848
          username: nacos  # 如果启用认证
          password: nacos
          namespace: public
          
          # 连接超时配置
          config-long-poll-timeout: 30000
          config-retry-time: 3000
```

### 2. 运行时问题

#### 2.1 工具调用失败

**问题现象**:
```json
{
  "success": false,
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "No healthy instances available"
  }
}
```

**排查步骤**:
```bash
# 1. 检查注册的服务
curl http://localhost:8080/mcp/bridge/services

# 2. 检查服务健康状态
curl http://localhost:8080/mcp/bridge/health/mcp-server-v6

# 3. 检查连接池状态
curl http://localhost:8080/mcp/bridge/connections/status

# 4. 查看详细日志
grep "SERVICE_UNAVAILABLE" logs/mcp-bridge-v3.log
grep "routeRequest" logs/mcp-bridge-v3.log
```

**解决方案**:
```bash
# 1. 重启目标服务
systemctl restart mcp-server-v6

# 2. 手动触发服务发现
curl -X POST http://localhost:8080/mcp/bridge/admin/discovery/refresh

# 3. 检查服务配置
curl http://mcp-server-v6:8066/actuator/health

# 4. 调整健康检查配置
```

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          load-balancer:
            health-check:
              failure-threshold: 5  # 增加容错次数
              interval: 15s  # 缩短检查间隔
```

#### 2.2 请求超时

**问题现象**:
```
java.util.concurrent.TimeoutException: 
Did not observe any item or terminal signal within 30000ms
```

**排查步骤**:
```bash
# 1. 检查网络延迟
ping mcp-server-v6
traceroute mcp-server-v6

# 2. 检查目标服务响应时间
curl -w "@curl-format.txt" http://mcp-server-v6:8066/actuator/health

# 3. 查看性能指标
curl http://localhost:8080/actuator/metrics/mcp.bridge.request.duration

# 4. 检查线程池状态
curl http://localhost:8080/actuator/metrics/executor
```

**解决方案**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          routing:
            timeout: 60s  # 增加超时时间
            retry:
              enabled: true
              max-attempts: 3
              backoff-delay: 2s

          connection-pool:
            connection-timeout: 30s
            socket-timeout: 60s
```

#### 2.3 内存泄漏

**问题现象**:
```
java.lang.OutOfMemoryError: Java heap space
```

**排查步骤**:
```bash
# 1. 查看内存使用情况
jstat -gc <pid> 5s
jmap -histo <pid> | head -20

# 2. 生成堆转储
jmap -dump:format=b,file=heap.dump <pid>

# 3. 检查连接池泄漏
curl http://localhost:8080/mcp/bridge/connections/status

# 4. 查看缓存使用情况
curl http://localhost:8080/actuator/metrics/cache.size
```

**解决方案**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          connection-pool:
            leak-detection-threshold: 30s  # 启用连接泄漏检测
            max-lifetime: 1800s  # 设置连接最大生命周期

          cache:
            tool-results:
              max-size: 1000  # 限制缓存大小
              ttl: 300s  # 设置过期时间

# JVM 参数调优
JAVA_OPTS="-Xms2g -Xmx4g -XX:+HeapDumpOnOutOfMemoryError"
```

### 3. 性能问题

#### 3.1 响应时间慢

**问题现象**:
- API 响应时间超过预期
- 用户体验变差

**性能分析**:
```bash
# 1. 查看响应时间分布
curl http://localhost:8080/actuator/metrics/http.server.requests

# 2. 检查线程池状态
curl http://localhost:8080/actuator/metrics/executor.active

# 3. 分析慢查询
grep "SLOW_REQUEST" logs/mcp-bridge-v3.log

# 4. 检查 GC 情况
jstat -gc <pid> 5s 10
```

**优化方案**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          # 启用缓存
          cache:
            enabled: true
            tool-results:
              ttl: 300s
              max-size: 2000

          # 优化连接池
          connection-pool:
            max-connections: 100
            min-idle-connections: 20

          # 并行处理
          routing:
            parallel-processing: true
            max-concurrent-requests: 500

# 服务器优化
server:
  tomcat:
    threads:
      max: 200
      min-spare: 20
```

#### 3.2 高并发问题

**问题现象**:
```
org.springframework.web.reactive.function.client.WebClientRequestException: 
Connection pool shut down
```

**排查步骤**:
```bash
# 1. 监控并发请求数
curl http://localhost:8080/actuator/metrics/http.server.requests.active

# 2. 检查连接池状态
curl http://localhost:8080/actuator/metrics/connection.pool.active

# 3. 查看系统负载
top
htop
sar -u 5 5

# 4. 检查网络连接
ss -tuln | wc -l
netstat -an | grep ESTABLISHED | wc -l
```

**解决方案**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          connection-pool:
            max-connections: 200  # 增加连接池大小
            max-connections-per-route: 50
            connection-timeout: 10s
            socket-timeout: 30s

          routing:
            timeout: 30s
            max-concurrent-requests: 1000  # 增加并发限制

# 系统级优化
server:
  tomcat:
    max-connections: 10000
    accept-count: 1000
    threads:
      max: 500
```

### 4. 网络问题

#### 4.1 连接被拒绝

**问题现象**:
```
java.net.ConnectException: Connection refused
```

**排查步骤**:
```bash
# 1. 检查目标服务状态
systemctl status mcp-server-v6
docker ps | grep mcp-server

# 2. 检查端口监听
netstat -tlnp | grep 8066
ss -tlnp | grep 8066

# 3. 检查防火墙
iptables -L
firewall-cmd --list-all

# 4. 检查 DNS 解析
nslookup mcp-server-v6
dig mcp-server-v6
```

**解决方案**:
```bash
# 1. 启动目标服务
systemctl start mcp-server-v6

# 2. 开放防火墙端口
firewall-cmd --add-port=8066/tcp --permanent
firewall-cmd --reload

# 3. 修正服务配置
```

```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          service-discovery:
            prefer-ip-address: true  # 使用 IP 而非主机名
```

#### 4.2 网络超时

**问题现象**:
```
java.net.SocketTimeoutException: Read timed out
```

**解决方案**:
```yaml
spring:
  ai:
    alibaba:
      mcp:
        bridge:
          connection-pool:
            connection-timeout: 30s
            socket-timeout: 60s
            connection-request-timeout: 15s

          routing:
            timeout: 45s
            retry:
              enabled: true
              max-attempts: 3
              backoff-delay: 2s
```

### 5. 配置问题

#### 5.1 配置不生效

**问题现象**:
- 修改配置后没有生效
- 使用默认配置而非自定义配置

**排查步骤**:
```bash
# 1. 检查配置文件加载
curl http://localhost:8080/actuator/configprops

# 2. 检查环境变量
env | grep MCP
env | grep NACOS

# 3. 检查配置优先级
curl http://localhost:8080/actuator/env

# 4. 查看配置绑定日志
grep "ConfigurationProperties" logs/mcp-bridge-v3.log
```

**解决方案**:
```bash
# 1. 确认配置文件路径
java -jar mcp-bridge-v3.jar --spring.config.location=file:./application.yml

# 2. 检查配置语法
yq eval 'length' application.yml

# 3. 使用环境变量覆盖
export SPRING_AI_ALIBABA_MCP_NACOS_SERVER_ADDR=127.0.0.1:8848

# 4. 启用配置刷新
curl -X POST http://localhost:8080/actuator/refresh
```

#### 5.2 Nacos 配置同步失败

**问题现象**:
```
Failed to sync configuration from Nacos
```

**排查步骤**:
```bash
# 1. 检查 Nacos 配置存在性
curl 'http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=mcp-bridge-v3&group=DEFAULT_GROUP'

# 2. 检查配置格式
curl 'http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=mcp-bridge-v3&group=DEFAULT_GROUP' | jq .

# 3. 查看配置监听器
grep "ConfigChangeEvent" logs/mcp-bridge-v3.log
```

**解决方案**:
```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: true
        server-addr: ${spring.ai.alibaba.mcp.nacos.server-addr}
        file-extension: yaml
        refresh-enabled: true
        
        # 配置重试
        max-retry: 3
        config-retry-time: 2000
        config-long-poll-timeout: 30000
```

---

## 🛠️ 调试工具和命令

### 1. 日志分析工具

```bash
# 实时日志监控
tail -f logs/mcp-bridge-v3.log | grep ERROR

# 按级别过滤日志
grep "WARN\|ERROR" logs/mcp-bridge-v3.log

# 分析错误模式
awk '/ERROR/ {print $0}' logs/mcp-bridge-v3.log | sort | uniq -c | sort -nr

# 查看特定时间段日志
sed -n '/2025-01-12 10:00:00/,/2025-01-12 11:00:00/p' logs/mcp-bridge-v3.log
```

### 2. 性能分析工具

```bash
# JVM 性能分析
jstat -gcutil <pid> 5s
jstack <pid> > thread_dump.txt
jmap -histo <pid> | head -20

# 网络分析
netstat -an | grep :8080
ss -tuln | grep :8080
tcpdump -i any port 8080

# 系统资源监控
htop
iotop
sar -u 5 5
```

### 3. 健康检查脚本

```bash
#!/bin/bash
# MCP Bridge 健康检查脚本

SERVICE_URL="http://localhost:8080"
LOG_FILE="/var/log/mcp-bridge-health.log"

check_service_health() {
    local endpoint=$1
    local description=$2
    
    echo "$(date): Checking $description..." | tee -a $LOG_FILE
    
    response=$(curl -s -w "%{http_code}" -o /tmp/health_response $endpoint)
    
    if [ "$response" = "200" ]; then
        echo "$(date): ✅ $description - OK" | tee -a $LOG_FILE
        return 0
    else
        echo "$(date): ❌ $description - FAILED (HTTP $response)" | tee -a $LOG_FILE
        cat /tmp/health_response | tee -a $LOG_FILE
        return 1
    fi
}

# 执行健康检查
check_service_health "$SERVICE_URL/actuator/health" "Service Health"
check_service_health "$SERVICE_URL/actuator/health/nacos" "Nacos Connection"
check_service_health "$SERVICE_URL/mcp/bridge/services" "Service Discovery"

# 检查关键指标
curl -s "$SERVICE_URL/mcp/bridge/stats" | jq '.requests.successRate' > /tmp/success_rate
success_rate=$(cat /tmp/success_rate)

if (( $(echo "$success_rate < 95.0" | bc -l) )); then
    echo "$(date): ⚠️  Success rate below threshold: $success_rate%" | tee -a $LOG_FILE
fi

echo "$(date): Health check completed" | tee -a $LOG_FILE
```

### 4. 故障恢复脚本

```bash
#!/bin/bash
# MCP Bridge 故障自动恢复脚本

SERVICE_NAME="mcp-bridge-v3"
SERVICE_URL="http://localhost:8080"
MAX_RETRIES=3
RETRY_DELAY=10

restart_service() {
    echo "$(date): Attempting to restart $SERVICE_NAME..."
    systemctl restart $SERVICE_NAME
    sleep 30
}

check_and_recover() {
    local retry_count=0
    
    while [ $retry_count -lt $MAX_RETRIES ]; do
        # 检查服务健康状态
        if curl -s -f "$SERVICE_URL/actuator/health" > /dev/null; then
            echo "$(date): Service is healthy"
            return 0
        fi
        
        echo "$(date): Service unhealthy, attempt $((retry_count + 1))/$MAX_RETRIES"
        
        # 尝试重启服务
        restart_service
        
        # 清理连接池
        curl -s -X POST "$SERVICE_URL/mcp/bridge/admin/connections/reset" || true
        
        retry_count=$((retry_count + 1))
        
        if [ $retry_count -lt $MAX_RETRIES ]; then
            sleep $RETRY_DELAY
        fi
    done
    
    echo "$(date): Failed to recover service after $MAX_RETRIES attempts"
    return 1
}

# 执行恢复流程
check_and_recover
```

---

## 📊 监控和告警

### 1. 关键指标监控

```yaml
# Prometheus 监控配置
scrape_configs:
  - job_name: 'mcp-bridge'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

# 告警规则
groups:
  - name: mcp-bridge-alerts
    rules:
      - alert: MCP_Bridge_Down
        expr: up{job="mcp-bridge"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "MCP Bridge is down"
          
      - alert: MCP_Bridge_High_Error_Rate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          
      - alert: MCP_Bridge_High_Response_Time
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High response time detected"
```

### 2. 日志监控

```yaml
# ELK Stack 配置
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/mcp-bridge-v3.log
    fields:
      service: mcp-bridge
      environment: production
    multiline.pattern: '^\d{4}-\d{2}-\d{2}'
    multiline.negate: true
    multiline.match: after

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "mcp-bridge-logs-%{+yyyy.MM.dd}"
```

---

## 🆘 紧急响应流程

### 1. 服务完全不可用

**紧急处理步骤**:
1. 立即检查基础设施（网络、数据库、Nacos）
2. 查看服务日志确定故障原因
3. 尝试重启服务
4. 如果重启失败，回滚到上一个稳定版本
5. 通知相关团队和用户

**操作命令**:
```bash
# 1. 快速诊断
curl http://localhost:8080/actuator/health || echo "Service is down"
systemctl status mcp-bridge-v3

# 2. 查看关键日志
tail -100 logs/mcp-bridge-v3.log | grep -E "ERROR|FATAL"

# 3. 重启服务
systemctl restart mcp-bridge-v3

# 4. 验证恢复
sleep 30
curl http://localhost:8080/actuator/health
```

### 2. 部分功能异常

**处理步骤**:
1. 隔离问题范围
2. 检查特定服务连接状态
3. 手动触发服务发现刷新
4. 如需要，手动下线异常服务实例

**操作命令**:
```bash
# 1. 检查服务状态
curl http://localhost:8080/mcp/bridge/services

# 2. 刷新服务发现
curl -X POST http://localhost:8080/mcp/bridge/admin/discovery/refresh

# 3. 下线异常实例
curl -X POST http://localhost:8080/mcp/bridge/admin/services/mcp-server-v6/instances/instance-001/offline
```

---

## 📞 支持联系方式

### 技术支持
- **技术热线**: +86-400-xxx-xxxx
- **邮箱**: mcp-support@company.com
- **工单系统**: https://support.company.com

### 应急联系人
- **技术负责人**: 张三 (13800138000)
- **运维负责人**: 李四 (13900139000)
- **产品负责人**: 王五 (13700137000)

---

> 💡 **故障排查建议**: 
> 1. 先检查基础设施和网络连通性
> 2. 查看日志是最有效的排查方法
> 3. 系统监控指标可以快速定位问题范围
> 4. 建立完善的告警机制可以提前发现问题
> 5. 定期进行故障演练提高应急响应能力



