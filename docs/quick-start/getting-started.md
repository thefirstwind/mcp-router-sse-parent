# 快速开始指南

> 5分钟上手 MCP Router 项目

## 📋 前置要求

在开始之前，请确保您的系统已安装：

- ☕ **Java 17+** - [下载地址](https://adoptium.net/)
- 📦 **Maven 3.6+** - [下载地址](https://maven.apache.org/download.cgi)
- 🔑 **DeepSeek API Key** - [获取地址](https://platform.deepseek.com/)

### 验证安装

```bash
# 检查 Java 版本
java -version  # 应该显示 17 或更高

# 检查 Maven 版本
mvn -version   # 应该显示 3.6 或更高
```

---

## 🚀 三步快速启动

### 步骤 1: 克隆项目

```bash
git clone https://github.com/thefirstwind/mcp-router-sse-parent.git
cd mcp-router-sse-parent
```

### 步骤 2: 构建项目

```bash
# 构建所有模块
mvn clean install -DskipTests

# 预计耗时: 2-3 分钟
```

### 步骤 3: 启动服务

#### 3.1 启动 MCP Server

```bash
cd mcp-server-v6
mvn spring-boot:run
```

**验证**: 访问 http://localhost:8060/actuator/health
- 应该看到: `{"status":"UP"}`

#### 3.2 启动 MCP Client（新终端）

```bash
# 设置 API Key
export DEEPSEEK_API_KEY=your-api-key-here

cd mcp-client
mvn spring-boot:run
```

**验证**: 访问 http://localhost:8080/actuator/health
- 应该看到: `{"status":"UP"}`

---

## 🎯 第一个请求

### 测试 1: 列出所有人员

```bash
curl http://localhost:8080/persons/all
```

**预期输出**:
```json
[
  {
    "id": 1,
    "name": "Albert Einstein",
    "nationality": "German",
    ...
  }
]
```

### 测试 2: AI 自然语言查询

```bash
curl -X POST http://localhost:8080/persons/query \
  -H "Content-Type: application/json" \
  -d '{"query": "告诉我数据库中最年长的人是谁？"}'
```

**预期输出**:
```json
{
  "result": "数据库中最年长的人是...",
  "toolsUsed": ["getPersonById", "getAllPersons"]
}
```

---

## ✅ 成功指标

如果您看到以下内容，说明设置成功：

- [x] MCP Server 在 8060 端口运行
- [x] MCP Client 在 8080 端口运行
- [x] 能够列出所有人员
- [x] AI 查询返回正常结果

---

## 🐛 常见问题

### Q: 端口已被占用

```bash
# 查找占用端口的进程
lsof -i :8080  # 或 :8060

# 杀死进程
kill -9 <PID>
```

### Q: API Key 无效

确保设置了正确的环境变量：

```bash
echo $DEEPSEEK_API_KEY  # 应该显示您的 API key
```

### Q: 构建失败

尝试清理并重新构建：

```bash
mvn clean
mvn install -DskipTests -U
```

---

## 📚 下一步

恭喜！您已经成功运行了 MCP Router 项目。

**接下来您可以**:

- [了解架构设计](../explanations/architecture.md)
- [添加第一个 MCP Server](../how-to-guides/add-mcp-server.md)
- [开发 AI Agent](../how-to-guides/add-agent.md)
- [查看 API 参考](../reference/api.md)

---

**需要帮助？** [创建 Issue](https://github.com/thefirstwind/mcp-router-sse-parent/issues/new) 或查看 [故障排除指南](../how-to-guides/troubleshooting.md)
