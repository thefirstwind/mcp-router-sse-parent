# Nacos Ephemeral 实例修复指南

## 📖 概述

本修复解决了 MCP Router 和 MCP Server 在崩溃后无法自动从 Nacos 清理的问题。通过设置 `ephemeral=true`，服务实例现在会在崩溃后 15-30 秒内自动从注册中心删除。

## 🔍 问题背景

### 修复前的问题

```
┌─────────────┐
│ MCP Server  │  Crash! (kill -9)
└─────────────┘
       ↓
  ❌ 问题: 实例仍在 Nacos 中
       ↓
┌──────────────┐
│    Nacos     │  僵尸实例 👻
│  (Registry)  │  - 无法连接
└──────────────┘  - 影响负载均衡
       ↓            - 需手动清理
  需要运维人员手动删除
```

### 修复后的效果

```
┌─────────────┐
│ MCP Server  │  Crash! (kill -9)
│ephemeral:   │
│    true     │
└─────────────┘
       ↓
  ✅ 15-30秒后自动清理
       ↓
┌──────────────┐
│    Nacos     │  实例已删除 ✓
│  (Registry)  │  - 自动清理
└──────────────┘  - 无僵尸实例
```

## ✅ 修复内容

### 1. mcp-router-v3 修复

**文件:** `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterNacosRegistration.java`

**第 56 行添加:**
```java
instance.setEphemeral(true);  // 设置为临时实例，崩溃后自动清理
```

### 2. mcp-server-v6 验证

**文件:** `mcp-server-v6/src/main/resources/application.yml`

**确认配置:**
```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          registry:
            enabled: true
            service-ephemeral: true  # ✅ 默认值，无需修改
```

## 🚀 快速开始

### 1. 重新编译

```bash
# 编译 Router
cd mcp-router-v3
mvn clean install -DskipTests

# 编译 Server
cd ../mcp-server-v6
mvn clean install -DskipTests
```

### 2. 启动服务

```bash
# 启动 Router (后台运行)
cd mcp-router-v3
java -jar target/mcp-router-v3-1.0.0.jar &

# 启动 Server (后台运行)
cd ../mcp-server-v6
java -jar target/mcp-server-v6-1.0.0.jar &
```

### 3. 验证修复

```bash
# 运行自动化测试脚本
cd ..
./test-ephemeral-fix.sh
```

**期望输出:**
```
==========================================
  Nacos Ephemeral 实例修复测试
==========================================

[1/6] 检查服务运行状态...
✓ Router PID: 12345
✓ Server PID: 12346

[2/6] 检查 Nacos 注册状态（崩溃前）...
  Router 实例数: 1
  Server 实例数: 1

[3/6] 验证 ephemeral 属性...
✓ Router ephemeral: true
✓ Server ephemeral: true

[4/6] 模拟服务崩溃 (kill -9)...
  终止 Router (PID: 12345)
  终止 Server (PID: 12346)
✓ 服务已强制终止

[5/6] 等待 Nacos 自动清理实例...
  Nacos 临时实例清理时间: 15-30 秒
  等待中... 20 秒 ✓ 实例已清理

[6/6] 验证清理结果...
  Router 实例数: 1 → 0
  Server 实例数: 1 → 0

==========================================
✓ 测试通过！
  所有实例已自动清理，ephemeral 修复成功！
==========================================
```

## 🔧 手动验证步骤

### 步骤 1: 检查实例的 ephemeral 属性

```bash
# 检查 Router
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-router-v3&groupName=mcp-server" \
  | python3 -m json.tool | grep ephemeral

# 检查 Server
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v6&groupName=mcp-server" \
  | python3 -m json.tool | grep ephemeral
```

**期望输出:**
```json
"ephemeral": true
```

### 步骤 2: 模拟崩溃

```bash
# 查找进程 PID
jps | grep -E "McpRouter|McpServer"

# 强制终止（使用实际的 PID）
kill -9 <ROUTER_PID> <SERVER_PID>
```

### 步骤 3: 等待并验证清理

```bash
# 等待 20 秒
sleep 20

# 检查实例是否已清理
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-router-v3&groupName=mcp-server" \
  | python3 -c "import sys,json; print('实例数:', len(json.load(sys.stdin)['hosts']))"

curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v6&groupName=mcp-server" \
  | python3 -c "import sys,json; print('实例数:', len(json.load(sys.stdin)['hosts']))"
```

**期望输出:**
```
实例数: 0
实例数: 0
```

## 📚 技术细节

### Ephemeral 实例的工作机制

| 阶段 | 时间 | 状态 | 说明 |
|------|------|------|------|
| 正常运行 | T+0s | ✅ healthy=true | 定期发送心跳（5秒） |
| 服务崩溃 | T+0s | 💥 进程终止 | 停止发送心跳 |
| 第一次超时 | T+5s | ⚠️ 心跳丢失 | Nacos 未收到心跳 |
| 第二次超时 | T+10s | ⚠️ 心跳丢失 | Nacos 继续等待 |
| 标记不健康 | T+15s | ❌ healthy=false | 从健康实例中移除 |
| 自动删除 | T+30s | 🗑️ 实例删除 | 从注册中心完全移除 |

### 配置参数说明

```properties
# Nacos 客户端默认配置
nacos.naming.heartbeat.interval=5000ms        # 心跳间隔
nacos.naming.heartbeat.timeout=15000ms        # 心跳超时（标记不健康）
nacos.naming.ip-delete-timeout=30000ms        # 删除超时（完全移除）
```

### 临时实例 vs 持久化实例

| 特性 | 临时实例 (ephemeral=true) | 持久化实例 (ephemeral=false) |
|------|---------------------------|------------------------------|
| **存储** | 内存 | 磁盘 |
| **性能** | 高 | 相对较低 |
| **健康检查** | 心跳上报 | 主动探测 |
| **崩溃处理** | 自动删除 | 需手动清理 |
| **适用场景** | 微服务实例 | 配置服务、静态服务 |
| **推荐** | ✅ MCP Router/Server | ❌ 不推荐 |

## ❓ 常见问题

### Q1: 为什么选择 ephemeral=true？

**A:** 微服务架构下的服务实例应该使用临时实例，因为：
1. **自动故障恢复**: 崩溃后无需人工干预
2. **准确的服务列表**: 只包含真正可用的实例
3. **云原生标准**: 符合容器化、弹性伸缩的最佳实践
4. **更好的性能**: 内存存储，响应更快

### Q2: 实例会不会被误删？

**A:** 不会。只有在以下情况才会删除：
- 连续 15 秒未收到心跳
- 进程已经终止或网络完全中断
- 正常的网络波动不会触发删除

### Q3: 30 秒清理时间太长怎么办？

**A:** 可以调整 Nacos Server 配置：
```properties
# application.properties (Nacos Server)
nacos.naming.expireTime=20000  # 改为 20 秒
```

但不建议设置太短，可能导致网络抖动时误删实例。

### Q4: 如何监控实例清理？

**A:** 查看 Nacos 日志：
```bash
tail -f /Users/shine/logs/nacos/naming.log | grep -E "deregister|delete|expire"
```

### Q5: 已有的僵尸实例如何清理？

**A:** 手动删除：
```bash
curl -X DELETE "http://127.0.0.1:8848/nacos/v1/ns/instance?serviceName=mcp-server-v6&ip=192.168.0.102&port=8066&groupName=mcp-server"
```

## 🎯 最佳实践

### 1. 开发环境

```yaml
# application-dev.yml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          registry:
            service-ephemeral: true  # 开发环境也使用临时实例
```

### 2. 生产环境

```yaml
# application-prod.yml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          registry:
            service-ephemeral: true  # 生产环境必须使用临时实例
            
# 配合健康检查和优雅停机
management:
  endpoint:
    health:
      enabled: true
  health:
    nacos:
      enabled: true
      
server:
  shutdown: graceful  # 优雅停机
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # 停机超时
```

### 3. 监控告警

建议监控以下指标：
- 实例注册/注销频率（异常频繁说明服务不稳定）
- 心跳成功率（低于 95% 需要关注）
- 实例存活时间分布（短于 1 分钟的实例过多说明有问题）

### 4. 日志追踪

建议开启以下日志：
```yaml
logging:
  level:
    com.alibaba.nacos.client.naming: DEBUG  # Nacos 客户端日志
    com.pajk.mcpbridge: DEBUG               # 应用日志
```

## 📁 相关文件

- **修复详细报告**: [NACOS_EPHEMERAL_FIX_REPORT.md](NACOS_EPHEMERAL_FIX_REPORT.md)
- **修复总结**: [EPHEMERAL_FIX_SUMMARY.md](EPHEMERAL_FIX_SUMMARY.md)
- **测试脚本**: [test-ephemeral-fix.sh](test-ephemeral-fix.sh)

## 🔗 参考资料

- [Nacos 官方文档 - 服务注册](https://nacos.io/zh-cn/docs/open-api.html)
- [Spring Cloud Alibaba - Nacos Discovery](https://github.com/alibaba/spring-cloud-alibaba/wiki/Nacos-discovery)
- [临时实例与持久化实例](https://nacos.io/zh-cn/docs/architecture.html)

## 💡 贡献者

如果你发现任何问题或有改进建议，欢迎提交 Issue 或 Pull Request。

---

**修复版本:** v1.0.0  
**最后更新:** 2025-10-30  
**状态:** ✅ 已验证通过


