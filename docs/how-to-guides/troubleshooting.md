# 故障排除指南

> 常见问题和解决方案

## 📋 目录

1. [启动问题](#启动问题)
2. [连接问题](#连接问题)
3. [性能问题](#性能问题)
4. [配置问题](#配置问题)

---

## 启动问题

### 问题 1: 端口已被占用

**症状**:
```
Web server failed to start. Port 8080 was already in use.
```

**原因**: 端口被其他进程占用

**解决方案**:

```bash
# 方案 1: 查找并杀死占用进程
lsof -i :8080
kill -9 <PID>

# 方案 2: 修改端口
# 编辑 application.yml
server:
  port: 8081  # 改为其他端口
```

---

### 问题 2: Java 版本不匹配

**症状**:
```
Unsupported class file major version 61
```

**原因**: Java 版本低于 17

**解决方案**:

```bash
# 检查版本
java -version

# 安装 Java 17+
# macOS
brew install openjdk@17

# Linux
sudo apt install openjdk-17-jdk

# 设置 JAVA_HOME
export JAVA_HOME=/path/to/java17
```

---

### 问题 3: Maven 构建失败

**症状**:
```
Failed to execute goal on project mcp-router-v3
```

**原因**: 依赖下载失败或版本冲突

**解决方案**:

```bash
# 清理并重新构建
mvn clean install -U -DskipTests

# 如果还失败，删除本地仓库缓存
rm -rf ~/.m2/repository/com/alibaba/cloud/ai
mvn clean install
```

---

## 连接问题

### 问题 4: 无法连接到 Nacos

**症状**:
```
Connection refused: localhost/127.0.0.1:8848
```

**原因**: Nacos 未启动或配置错误

**解决方案**:

```bash
# 1. 检查 Nacos 是否运行
curl http://localhost:8848/nacos/

# 2. 启动 Nacos (如果未运行)
cd nacos/bin
./startup.sh -m standalone

# 3. 检查配置
# application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848  # 确保正确
```

---

### 问题 5: SSE 连接断开

**症状**:
```
SSE connection lost, retrying...
```

**原因**: 网络问题或服务器重启

**解决方案**:

```yaml
# 增加超时时间
spring:
  webflux:
    sse:
      timeout: 600s  # 默认 30s
      
# 启用自动重连
mcp:
  client:
    auto-reconnect: true
    retry-interval: 5s
    max-retries: 3
```

---

### 问题 6: DeepSeek API 调用失败

**症状**:
```
401 Unauthorized: Invalid API key
```

**原因**: API Key 未设置或无效

**解决方案**:

```bash
# 1. 检查 API Key
echo $DEEPSEEK_API_KEY

# 2. 设置 API Key
export DEEPSEEK_API_KEY=sk-xxxx

# 3. 或在 application.yml 中配置
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
```

---

## 性能问题

### 问题 7: 响应慢

**症状**: API 响应时间 > 5秒

**诊断**:

```bash
# 1. 检查日志
tail -f logs/application.log | grep "took"

# 2. 启用 metrics
curl http://localhost:8080/actuator/metrics/http.server.requests
```

**解决方案**:

```yaml
# 1. 启用缓存
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=60s

# 2. 增加连接池
spring:
  webflux:
    client:
      pool:
        max-connections: 500
        pending-acquire-timeout: 10s

# 3. 启用数据库连接池
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

---

### 问题 8: 内存溢出

**症状**:
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**:

```bash
# 增加 JVM 内存
java -Xms2g -Xmx4g -jar app.jar

# 或在启动脚本中
export JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
mvn spring-boot:run
```

---

## 配置问题

### 问题 9: 配置未生效

**症状**: 修改配置后没有变化

**解决方案**:

```bash
# 1. 确认使用了正确的 profile
mvn spring-boot:run -Dspring.profiles.active=dev

# 2. 检查配置优先级
# 优先级从高到低:
# - 命令行参数
# - application-{profile}.yml
# - application.yml
# - 默认值

# 3. 查看实际配置
curl http://localhost:8080/actuator/env
```

---

### 问题 10: 日志级别设置无效

**症状**: 看不到 DEBUG 日志

**解决方案**:

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.nacos.mcp: DEBUG  # 设置包级别
    org.springframework.ai: DEBUG
  file:
    name: logs/application.log
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

---

## 🔍 诊断工具

### 1. 健康检查

```bash
# 检查所有服务健康状态
curl http://localhost:8080/actuator/health

# 详细健康信息
curl http://localhost:8080/actuator/health?show-details=always
```

### 2. 查看 Metrics

```bash
# JVM 内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# HTTP 请求统计
curl http://localhost:8080/actuator/metrics/http.server.requests
```

### 3. 线程 Dump

```bash
# 获取线程转储
curl http://localhost:8080/actuator/threaddump > threaddump.json

# 分析死锁
jstack <PID> | grep -A 10 "deadlock"
```

### 4. 堆 Dump

```bash
# 生成堆转储
jmap -dump:format=b,file=heap.bin <PID>

# 分析
jhat heap.bin
# 访问 http://localhost:7000
```

---

## 📊 日志分析

### 常见错误日志

#### 1. Connection Timeout

```log
ERROR - Connection timeout after 30000ms
```

**解决**: 增加超时时间或检查网络

#### 2. NullPointerException

```log
ERROR - java.lang.NullPointerException at ...
```

**解决**: 检查null检查，添加@NonNull注解

#### 3. JSON Parse Error

```log
ERROR - Cannot deserialize value of type ...
```

**解决**: 检查JSON格式，添加@JsonProperty

---

## 🆘 获取帮助

如果问题仍未解决:

### 1. 收集信息

```bash
# 生成诊断报告
./scripts/generate-diagnostic-report.sh

# 包含:
# - application.log
# - heap dump (如果)
# - thread dump
# - 配置文件
# - 依赖版本
```

### 2. 创建 Issue

访问: https://github.com/thefirstwind/mcp-router-sse-parent/issues/new

**包含信息**:
- [ ] 问题描述
- [ ] 错误日志
- [ ] 环境信息 (OS, Java版本)
- [ ] 重现步骤
- [ ] 预期行为
- [ ] 实际行为

### 3. 社区支持

- [GitHub Discussions](https://github.com/thefirstwind/mcp-router-sse-parent/discussions)
- [Stack Overflow](https://stackoverflow.com/questions/tagged/mcp-router)

---

## 📚 相关文档

- [快速开始](../quick-start/getting-started.md)
- [配置参考](../reference/configuration.md)
- [API 参考](../reference/api.md)
- [架构设计](../explanations/architecture.md)

---

**找到解决方案了吗？** 欢迎分享您的经验！
