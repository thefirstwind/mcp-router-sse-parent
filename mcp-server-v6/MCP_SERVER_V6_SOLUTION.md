# MCP Server V6 配置更新解决方案（项目内实现）

## 🎯 约束条件

- ✅ **不修改 spring-ai-alibaba 依赖库**
- ✅ **只在 mcp-server-v6 项目内修改**
- ✅ **保持与框架的兼容性**

## 📋 解决方案总览

### 方案 1：版本号升级（推荐 - 最简单）⭐⭐⭐⭐⭐

每次工具定义变更时，升级版本号。

**优点**：
- ✅ 零代码修改
- ✅ 支持多版本共存
- ✅ 符合最佳实践

**实施步骤**：

1. 修改 `application.yml`：
```yaml
spring:
  ai:
    mcp:
      server:
        version: 1.0.2  # 从 1.0.1 升级
```

2. 重新部署即可

### 方案 2：启动前自动清理（新增 - 推荐）⭐⭐⭐⭐

在应用启动前自动删除旧配置，让框架创建新配置。

**优点**：
- ✅ 自动化处理
- ✅ 开发环境友好
- ✅ 不修改依赖库

**实施步骤**：

1. **已创建的文件**：
   - `McpServerConfigCleaner.java` - 配置清理器

2. **配置开关**：

在 `application.yml` 中添加：

```yaml
# MCP Server 配置管理
mcp:
  server:
    config:
      # 是否在启动前清理旧配置（开发环境推荐开启）
      clean-on-startup: true
      
      # 是否只清理不兼容的配置（生产环境推荐）
      clean-only-incompatible: true

spring:
  ai:
    mcp:
      server:
        name: mcp-server-v6
        version: 1.0.1
```

3. **使用场景**：

| 场景 | clean-on-startup | clean-only-incompatible | 说明 |
|------|------------------|-------------------------|------|
| 开发环境 | true | false | 每次启动都清理，方便调试 |
| 测试环境 | true | true | 只清理不兼容配置 |
| 生产环境 | false | true | 不自动清理，使用版本号升级 |

### 方案 3：手动清理 + 版本升级（最稳妥）⭐⭐⭐⭐⭐

结合方案 1 和手动操作。

**适用场景**：生产环境发布

**步骤**：

1. **部署前清理**：
```bash
# 使用提供的清理脚本
cd /Users/shine/projects.mcp-router-sse-parent/scripts
./cleanup-nacos-configs.sh interactive
```

2. **升级版本号**：
```yaml
spring:
  ai:
    mcp:
      server:
        version: 1.0.2
```

3. **部署新版本**

4. **验证**：
```bash
# 检查 Nacos 配置
curl "http://localhost:8848/nacos/v1/cs/configs?search=accurate&dataId=&group=mcp-server"

# 检查服务实例
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v6"
```

## 🔧 详细实施指南

### 方案 2 的完整配置

#### 1. 添加依赖（如果需要）

检查 `pom.xml` 是否已包含：

```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <groupId>nacos-spring-context</groupId>
    <artifactId>nacos-maintainer-client</artifactId>
    <!-- 版本号从父 POM 继承 -->
</dependency>
```

#### 2. 完整的 application.yml 配置

```yaml
# Server
server:
  port: 8066
  address: 0.0.0.0

# Spring Boot
spring:
  application:
    name: mcp-server-v6
    
  # MCP Server 配置
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.0.1  # 每次工具变更时升级此版本号
        type: ASYNC
        instructions: "This reactive server provides tools"
        sse-message-endpoint: /mcp/message
        sse-endpoint: /sse
        capabilities:
          tool: true
          resource: true
          prompt: true
          completion: true
          
    # Nacos 配置
    alibaba:
      mcp:
        nacos:
          namespace: public
          server-addr: 127.0.0.1:8848
          username: nacos
          password: nacos
          registry:
            enabled: true
            service-group: mcp-server
            service-name: ${spring.application.name}

# MCP Server 自定义配置（用于配置清理器）
mcp:
  server:
    config:
      # 【开发环境】设置为 true，可以自动清理旧配置
      # 【生产环境】设置为 false，使用版本号升级策略
      clean-on-startup: ${MCP_CLEAN_ON_STARTUP:false}
      
      # 是否只清理不兼容的配置
      clean-only-incompatible: true

# Logging
logging:
  level:
    com.nacos.mcp: DEBUG
    com.alibaba.cloud.ai.mcp: DEBUG
    root: INFO
```

#### 3. 环境变量配置

可以通过环境变量控制清理行为：

```bash
# 开发环境
export MCP_CLEAN_ON_STARTUP=true
java -jar mcp-server-v6.jar

# 生产环境
export MCP_CLEAN_ON_STARTUP=false
java -jar mcp-server-v6.jar
```

## 📊 方案对比

| 方案 | 难度 | 自动化程度 | 生产环境适用 | 开发环境适用 |
|------|------|-----------|-------------|-------------|
| 方案 1：版本升级 | ⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 方案 2：自动清理 | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 方案 3：手动清理 | ⭐⭐ | ⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |

## 🚀 推荐实施路径

### 阶段 1：开发环境（立即实施）

```yaml
# application-dev.yml
mcp:
  server:
    config:
      clean-on-startup: true
      clean-only-incompatible: false  # 总是清理

spring:
  ai:
    mcp:
      server:
        version: 1.0.1-SNAPSHOT  # 开发版本
```

**好处**：
- 🚀 快速迭代，不用手动清理
- 🧪 自动测试工具变更

### 阶段 2：测试环境

```yaml
# application-test.yml
mcp:
  server:
    config:
      clean-on-startup: true
      clean-only-incompatible: true  # 只清理不兼容的

spring:
  ai:
    mcp:
      server:
        version: 1.0.1-RC  # 候选版本
```

### 阶段 3：生产环境

```yaml
# application-prod.yml
mcp:
  server:
    config:
      clean-on-startup: false  # 不自动清理

spring:
  ai:
    mcp:
      server:
        version: 1.0.2  # 正式版本，每次发布升级
```

**发布流程**：
1. 升级版本号
2. 手动清理旧配置（可选）
3. 部署新版本
4. 验证功能
5. 清理旧版本配置

## 🔍 验证和调试

### 1. 查看启动日志

启用配置清理后，应该看到：

```
🧹 Starting MCP server config cleanup check...
📋 Server: mcp-server-v6, Version: 1.0.1
📦 Found existing config in Nacos: mcp-server-v6 v1.0.1
🗑️ Deleting old MCP server config: mcp-server-v6 v1.0.1
✅ Successfully deleted old config
```

### 2. 测试配置更新

```bash
# 1. 修改工具定义（添加字段或修改参数）
vim src/main/java/com/nacos/mcp/server/v6/tools/PersonManagementTool.java

# 2. 重新编译
mvn clean package

# 3. 启动服务（配置清理器会自动工作）
java -jar target/mcp-server-v6-*.jar

# 4. 检查 Nacos 配置是否更新
curl "http://localhost:8848/nacos/v1/cs/configs?dataId=mcp-server-v6&group=mcp-server" | jq .
```

### 3. 手动触发清理

如果需要手动清理：

```bash
# 使用 Nacos Open API
curl -X DELETE \
  "http://localhost:8848/nacos/v1/cs/configs" \
  -d "dataId=mcp-server-v6-1.0.1-mcp-tools.json" \
  -d "group=mcp-tools"
```

## ⚠️ 注意事项

### 1. 配置清理器的限制

- ⚠️ `deleteMcpServer` API 需要确认是否在你的 Nacos 版本中可用
- ⚠️ 如果 API 不可用，需要使用 Nacos Config API 直接删除配置文件

### 2. 兼容性检查

当前实现的 `McpServerConfigCleaner` 会删除旧配置，如果你担心误删，可以：

1. 先备份配置：
```bash
./scripts/cleanup-nacos-configs.sh analyze
```

2. 设置只在不兼容时清理：
```yaml
mcp:
  server:
    config:
      clean-only-incompatible: true
```

### 3. 生产环境建议

**不建议在生产环境开启自动清理**，原因：
- ❌ 可能误删正在使用的配置
- ❌ 无法回滚到旧版本
- ❌ 缺少审计日志

**推荐生产环境流程**：
1. 使用版本号管理（方案 1）
2. 发布前手动评估和清理
3. 保留最近 3 个版本配置
4. 建立变更记录

## 📝 总结

### 最佳实践组合

| 环境 | 主要方案 | 辅助方案 |
|------|---------|---------|
| **开发** | 方案 2（自动清理） | - |
| **测试** | 方案 2（自动清理） | 方案 1（版本号） |
| **生产** | 方案 1（版本号升级） | 方案 3（手动清理） |

### 关键配置

```yaml
# 【开发】application-dev.yml
mcp.server.config.clean-on-startup: true

# 【生产】application-prod.yml
mcp.server.config.clean-on-startup: false
spring.ai.mcp.server.version: [升级版本号]
```

### 已创建文件

- ✅ `McpServerConfigCleaner.java` - 配置清理器
- ✅ `MCP_SERVER_V6_SOLUTION.md` - 本文档

## 🔗 相关文档

- `NACOS_CONFIG_UPDATE_COMPLETE_GUIDE.md` - 完整解决方案对比
- `OLD_CONFIG_MIGRATION_GUIDE.md` - 老配置迁移指南
- `scripts/cleanup-nacos-configs.sh` - 配置清理脚本

---

**最后更新**：2026-01-29 14:50
**维护者**：MCP Router Team
