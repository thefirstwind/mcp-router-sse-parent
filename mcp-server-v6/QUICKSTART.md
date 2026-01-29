# MCP-Server-V6 配置更新问题 - 快速开始

## 🎯 问题

当 mcp-server-v6 的工具定义发生变化（添加字段、修改参数等）后，重新部署时：
- ❌ Spring AI Alibaba 会检测到不兼容
- ❌ 抛出异常，导致服务无法启动
- ❌ 必须手动删除 Nacos 中的旧配置

## ✅ 解决方案（选择一个）

### 方案 A：版本号升级（最简单）⭐⭐⭐⭐⭐

**适用场景**：生产环境、正式发布

**步骤**：

1. 修改 `src/main/resources/application.yml`：
```yaml
spring:
  ai:
    mcp:
      server:
        version: 1.0.2  # 👈 从 1.0.1 升级到 1.0.2
```

2. 重新编译和部署：
```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-server-v6
mvn clean package
java -jar target/mcp-server-v6-*.jar
```

**优点**：
- ✅ 零风险
- ✅ 支持版本共存
- ✅ 可以回滚

### 方案 B：自动清理（推荐开发环境）⭐⭐⭐⭐

**适用场景**：开发环境、快速迭代

**步骤**：

1. 启用配置清理：
```bash
# 设置环境变量
export MCP_CLEAN_ON_STARTUP=true

# 或者直接修改 application.yml
# mcp.server.config.clean-on-startup: true
```

2. 启动服务：
```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-server-v6
mvn clean package
java -jar target/mcp-server-v6-*.jar
```

3. 查看日志，确认清理成功：
```
🧹 Starting MCP server config cleanup check...
🗑️ Deleting old MCP server config...
✅ Successfully deleted old config
```

**优点**：
- ✅ 全自动
- ✅ 不用手动升级版本号
- ✅ 开发效率高

**注意**：
- ⚠️ 不建议在生产环境使用

## 📝 已创建的文件

```
mcp-server-v6/
├── src/main/java/com/nacos/mcp/server/v6/config/
│   └── McpServerConfigCleaner.java  ← ✅ 自动清理器
├── src/main/resources/
│   └── application.yml              ← ✅ 已添加配置
└── MCP_SERVER_V6_SOLUTION.md        ← ✅ 详细文档
```

## 🚀 推荐使用方式

### 开发环境

```bash
# 1. 设置环境变量（自动清理）
export MCP_CLEAN_ON_STARTUP=true

# 2. 启动服务
cd mcp-server-v6
mvn spring-boot:run
```

### 生产环境

```bash
# 1. 升级版本号
# 编辑 application.yml，修改版本号

# 2. 构建和部署
mvn clean package
java -jar target/mcp-server-v6-*.jar
```

## ❓ 常见问题

### Q1: 启动时报错 "check mcp server compatible false"

**原因**：工具定义与 Nacos 中的配置不兼容

**解决**：
- 方案 A：升级版本号
- 方案 B：启用自动清理
- 手动清理：删除 Nacos 中的旧配置

### Q2: 如何手动删除 Nacos 配置？

```bash
# 方法 1：使用脚本
cd /Users/shine/projects.mcp-router-sse-parent/scripts
./cleanup-nacos-configs.sh interactive

# 方法 2：Nacos 控制台
# 访问 http://localhost:8848/nacos
# 进入配置管理 → 配置列表 → 删除相关配置
```

### Q3: 如何知道配置是否更新成功？

```bash
# 检查 Nacos 配置
curl "http://localhost:8848/nacos/v1/cs/configs?dataId=mcp-server-v6&group=mcp-server" | jq .

# 检查工具定义
curl "http://localhost:8848/nacos/v1/cs/configs?dataId=mcp-server-v6-mcp-tools.json&group=mcp-tools" | jq .
```

## 📚 更多信息

- 详细方案说明：`MCP_SERVER_V6_SOLUTION.md`
- 整体对比：`/NACOS_CONFIG_UPDATE_COMPLETE_GUIDE.md`
- mcp-server-v5 方案：`/NACOS_CONFIG_ISSUE_SUMMARY.md`

---

**快速链接**：
- [完整文档](./MCP_SERVER_V6_SOLUTION.md)
- [配置清理器源码](./src/main/java/com/nacos/mcp/server/v6/config/McpServerConfigCleaner.java)
- [应用配置](./src/main/resources/application.yml)
