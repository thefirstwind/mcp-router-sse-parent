# Nacos Ephemeral 实例修复报告

## 问题描述

之前的实现中，mcp-router-v3 和 mcp-server-v6 注册到 Nacos 时没有设置 `ephemeral=true`，导致：
- 服务崩溃或异常终止后，实例仍然保留在 Nacos 注册中心
- 需要手动清理僵尸实例
- 影响服务发现和负载均衡的准确性

## 修复方案

### 1. mcp-router-v3 修复

**文件：** `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterNacosRegistration.java`

**修改：**
```java
// 创建实例
Instance instance = new Instance();
instance.setIp(localIp);
instance.setPort(serverPort);
instance.setHealthy(true);
instance.setEnabled(true);
instance.setEphemeral(true);  // ✅ 设置为临时实例，崩溃后自动清理
```

### 2. mcp-server-v6 修复

mcp-server-v6 使用 Spring Cloud Alibaba Nacos Discovery，已经默认支持 ephemeral 实例。
确认配置正确使用 Nacos 的自动注册功能。

## 测试结果

### 测试场景：模拟服务崩溃

**测试步骤：**
1. 启动 mcp-router-v3（PID: 64508）和 mcp-server-v6（PID: 69269）
2. 验证两个服务都成功注册到 Nacos，且 `ephemeral=true`
3. 使用 `kill -9` 强制终止两个服务
4. 等待 20 秒后检查 Nacos 注册中心

**测试前状态：**
- mcp-router-v3: 1 个实例 (127.0.0.1:8052, ephemeral: true, healthy: true)
- mcp-server-v6: 1 个实例 (192.168.0.102:8066, ephemeral: true, healthy: true)

**崩溃后状态（20秒后）：**
- mcp-router-v3: 0 个实例 ✅
- mcp-server-v6: 0 个实例 ✅

### 关键日志证据

**Router 注册日志：**
```
2025-10-30 18:31:52.739 INFO [REGISTER-SERVICE] public registering service mcp-router-v3 
with instance Instance{ip='127.0.0.1', port=8052, ephemeral=true, ...}
```

**Server 注册日志：**
```
2025-10-30 18:33:05.836 INFO [REGISTER-SERVICE] public registering service mcp-server-v6 
with instance Instance{ip='192.168.0.102', port=8066, ephemeral=true, ...}
```

**Nacos API 验证（崩溃前）：**
```json
{
  "ip": "127.0.0.1",
  "port": 8052,
  "healthy": true,
  "ephemeral": true,  // ✅
  "serviceName": "mcp-server@@mcp-router-v3"
}
```

## 技术细节

### Ephemeral 实例的工作原理

1. **心跳检测：** 临时实例会定期向 Nacos 发送心跳（默认 5 秒）
2. **超时剔除：** 如果 15 秒内未收到心跳，标记为不健康
3. **自动清理：** 30 秒后自动从注册中心删除实例
4. **无需注销：** 服务崩溃时无需显式调用注销 API

### 持久化实例 vs 临时实例

| 特性 | 临时实例 (ephemeral=true) | 持久化实例 (ephemeral=false) |
|------|---------------------------|------------------------------|
| 存储方式 | 内存 | 磁盘持久化 |
| 崩溃处理 | 自动清理 | 手动清理 |
| 适用场景 | 微服务实例 | 配置、静态服务 |
| 性能 | 高 | 相对较低 |

## 验证清单

- [x] Router 注册时设置 ephemeral=true
- [x] Server 注册时设置 ephemeral=true
- [x] 正常启动时能成功注册
- [x] 崩溃后实例自动清理
- [x] 清理时间在合理范围内（< 30秒）
- [x] 无僵尸实例残留

## 影响评估

### 正面影响
1. ✅ 自动清理崩溃实例，无需人工干预
2. ✅ 提高服务发现的准确性
3. ✅ 减少运维成本
4. ✅ 符合云原生最佳实践

### 潜在风险
- 无（这是标准的微服务实例注册方式）

## 建议

1. **生产环境部署前：**
   - 在测试环境充分验证
   - 确认 Nacos 的健康检查间隔配置合理

2. **监控告警：**
   - 监控实例频繁上下线的情况
   - 设置告警阈值，及时发现异常

3. **文档更新：**
   - 在部署文档中说明 ephemeral 实例的特性
   - 提醒运维人员不要手动修改实例状态

## 总结

本次修复通过设置 `instance.setEphemeral(true)`，使得服务实例在崩溃后能够自动从 Nacos 注册中心清理，解决了僵尸实例问题。测试验证了修复的有效性，服务崩溃后 20 秒内实例被完全清理，符合预期。

---

**修复时间：** 2025-10-30  
**测试通过：** ✅  
**建议合并：** ✅


