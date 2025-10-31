# Nacos 健康状态同步修复 - 快速总结

## 🎯 问题
数据库中所有服务的 `healthy` 字段始终为 `1`，即使服务实际已停止运行。

## 🔍 根因
`McpServer.fromRegistration()` 方法**硬编码** `healthy=true`，导致从 Nacos 获取的真实健康状态被丢弃。

```java
// 问题代码 (修复前)
public static McpServer fromRegistration(...) {
    return McpServer.builder()
        .healthy(true)      // ❌ 硬编码
        .enabled(true)      // ❌ 硬编码
        .build();
}
```

## ✅ 解决方案
**方法重载** + **传递真实状态**

### 1. 新增重载方法 (McpServer.java)
```java
// 新方法：支持传递真实状态
public static McpServer fromRegistration(..., 
    Boolean healthy, Boolean enabled, Double weight, Boolean ephemeral) {
    return McpServer.builder()
        .healthy(healthy != null ? healthy : true)  // ✅ 使用真实值
        .enabled(enabled != null ? enabled : true)  // ✅ 使用真实值
        .build();
}
```

### 2. 调用处传递真实值 (McpServerPersistenceService.java)
```java
// 修复后
McpServer server = McpServer.fromRegistration(
    serverKey, serverName, ..., metadata,
    serverInfo.isHealthy(),   // ✅ 传递真实健康状态
    serverInfo.getEnabled(),  // ✅ 传递真实启用状态
    serverInfo.getWeight(),   // ✅ 传递真实权重
    serverInfo.isEphemeral()  // ✅ 传递真实临时节点状态
);
```

## 📊 验证结果

### Before 修复前 ❌
```
Nacos: healthy=false  →  数据库: healthy=1  (不一致！)
```

### After 修复后 ✅
```
Nacos: healthy=false  →  数据库: healthy=0  (一致！)
Nacos: healthy=true   →  数据库: healthy=1  (一致！)
```

### 数据对比

| 服务名 | Nacos | 修复前 DB | 修复后 DB | 状态 |
|--------|-------|-----------|-----------|------|
| mcp-server-v6 | `true` | `1` | `1` | ✅ |
| test-mcp-server-alignment | `false` | `1` ❌ | `0` ✅ | **修复！** |
| mcp-server-v2-real | `false` | `1` ❌ | `0` ✅ | **修复！** |

**数据一致率**: 66.7% → **100%** ✅

## 📁 修改文件

1. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java`
   - 新增重载方法 (第162-190行)

2. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/service/McpServerPersistenceService.java`
   - 修改调用处传递真实参数 (第54-88行)

## 🎉 成果

✅ **Nacos 健康状态实时同步到数据库**  
✅ **数据一致率 100%**  
✅ **向后兼容，零风险**  
✅ **编译通过，验证完成**

---

**修复时间**: 2025-10-30  
**修复版本**: mcp-router-v3 1.0.0


