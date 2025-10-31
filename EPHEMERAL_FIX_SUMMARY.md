# Nacos Ephemeral 实例修复总结

## 🎯 修复目标

解决服务崩溃后 Nacos 中残留僵尸实例的问题。

## 📋 修复内容

### 1. mcp-router-v3

**文件：** `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterNacosRegistration.java`

**修改行：** 第 56 行

**变更：**
```diff
  Instance instance = new Instance();
  instance.setIp(localIp);
  instance.setPort(serverPort);
  instance.setHealthy(true);
  instance.setEnabled(true);
+ instance.setEphemeral(true);  // 设置为临时实例，崩溃后自动清理
```

### 2. mcp-server-v6

**状态：** ✅ 已验证（使用 Spring Cloud Alibaba，默认 ephemeral=true）

Spring AI Alibaba MCP Nacos 组件默认配置：
- `spring.ai.alibaba.mcp.nacos.registry.service-ephemeral: true`（默认值）

## ✅ 测试验证

### 测试场景 1: 正常注册

**结果：** ✅ 通过

```bash
# Router
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-router-v3&groupName=mcp-server"
# 输出: ephemeral: true ✅

# Server  
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v6&groupName=mcp-server"
# 输出: ephemeral: true ✅
```

### 测试场景 2: 崩溃清理

**步骤：**
1. 启动服务：Router (PID: 64508), Server (PID: 69269)
2. 确认注册成功：2 个实例在线
3. 模拟崩溃：`kill -9 64508 69269`
4. 等待 20 秒后检查

**结果：** ✅ 通过

```
测试前：
  mcp-router-v3: 1 个实例
  mcp-server-v6: 1 个实例

崩溃后（20秒）：
  mcp-router-v3: 0 个实例 ✅
  mcp-server-v6: 0 个实例 ✅
```

**结论：** 实例自动清理成功，无僵尸实例残留！

## 📊 对比分析

### 修复前 vs 修复后

| 维度 | 修复前 (ephemeral=false) | 修复后 (ephemeral=true) |
|------|-------------------------|------------------------|
| 实例类型 | 持久化实例 | 临时实例 |
| 存储方式 | 磁盘 | 内存 |
| 崩溃后状态 | 实例残留 ❌ | 自动清理 ✅ |
| 清理时间 | 需手动清理 | 自动（15-30秒） |
| 运维成本 | 高 | 低 |
| 性能 | 一般 | 优秀 |

## 🔧 技术细节

### Nacos Ephemeral 实例机制

```
┌─────────────┐           ┌──────────────┐
│  MCP Server │◄─────────►│    Nacos     │
│  (Instance) │  Heartbeat│  (Registry)  │
└─────────────┘   (5s)    └──────────────┘
       │
       │ Crash (kill -9)
       ▼
   No Heartbeat
       │
       ▼
  15s: Mark Unhealthy
       │
       ▼
  30s: Auto Delete ✅
```

**时间线：**
- T+0s: 服务崩溃
- T+5s: 首次心跳超时
- T+10s: 第二次心跳超时
- T+15s: 标记为不健康 (healthy=false)
- T+30s: 从注册中心删除

### 相关配置

```properties
# Nacos 客户端配置（默认值）
nacos.naming.heartbeat.interval=5s         # 心跳间隔
nacos.naming.heartbeat.timeout=15s         # 心跳超时
nacos.naming.ip-delete-timeout=30s         # 删除超时
```

## 📝 代码审查清单

- [x] Router 设置 ephemeral=true
- [x] Server 使用正确的默认配置
- [x] 测试正常注册
- [x] 测试崩溃清理
- [x] 验证无副作用
- [x] 文档更新

## 🚀 部署建议

### 1. 验证配置

部署前确认以下配置：

**mcp-server-v6 application.yml:**
```yaml
spring:
  ai:
    alibaba:
      mcp:
        nacos:
          registry:
            enabled: true
            service-ephemeral: true  # 确保为 true（默认值）
```

### 2. 监控指标

建议监控以下指标：
- 实例注册/注销频率
- 心跳成功率
- 健康检查通过率
- 实例存活时间分布

### 3. 告警设置

建议设置以下告警：
- 实例频繁上下线（5分钟内 > 3次）
- 心跳失败率 > 10%
- 实例数量异常波动

## 🎓 最佳实践

### 何时使用 Ephemeral 实例

✅ **应该使用（推荐）：**
- 微服务实例（如 mcp-server、mcp-router）
- 容器化部署（Docker/K8s）
- 云原生应用
- 弹性伸缩场景

❌ **不应该使用：**
- 配置中心服务
- 静态服务列表
- 需要持久化的服务信息
- 长期运行的单例服务

### 服务端配置优化

如果需要调整清理时间，可在 Nacos Server 修改：

```properties
# application.properties
nacos.naming.data.warmup=true
nacos.naming.expireInstance=true
nacos.naming.expireTime=30000  # 30秒
```

## 📚 相关文档

- [Nacos 服务注册与发现](https://nacos.io/zh-cn/docs/open-api.html)
- [Spring Cloud Alibaba Nacos Discovery](https://github.com/alibaba/spring-cloud-alibaba/wiki/Nacos-discovery)
- [临时实例 vs 持久化实例](https://nacos.io/zh-cn/docs/architecture.html)

## 🔗 相关文件

- 修复详细报告: [NACOS_EPHEMERAL_FIX_REPORT.md](NACOS_EPHEMERAL_FIX_REPORT.md)
- Router 注册代码: [McpRouterNacosRegistration.java](mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterNacosRegistration.java)
- Server MCP 配置: [mcp-server-v6/application.yml](mcp-server-v6/src/main/resources/application.yml)

---

**修复完成时间：** 2025-10-30  
**测试状态：** ✅ 全部通过  
**建议操作：** 可以合并到主分支


