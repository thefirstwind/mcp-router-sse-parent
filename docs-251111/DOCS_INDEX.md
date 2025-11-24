# MCP Router V3 文档索引

> **最后更新**: 2025-03-01  
> **原则**: 最小必要性 - 一个功能一份文档

---

## 📚 核心文档（必读）

### 🔥 持久化功能（MySQL集成）

#### 基础文档
| 文档 | 说明 | 优先级 |
|------|------|--------|
| **[PERSISTENCE_README.md](PERSISTENCE_README.md)** | 📋 入口文档（3分钟了解） | ⭐⭐⭐⭐⭐ |
| **[PERSISTENCE_GUIDE.md](PERSISTENCE_GUIDE.md)** | 📖 完整指南（技术细节） | ⭐⭐⭐⭐⭐ |
| **[mcp-router-v3/database/schema.sql](../mcp-router-v3/database/schema.sql)** | 🗄️ 数据库Schema | ⭐⭐⭐⭐⭐ |
| **[mcp-router-v3/database/setup.sh](../mcp-router-v3/database/setup.sh)** | 🔧 一键安装脚本 | ⭐⭐⭐⭐⭐ |

#### 设计文档 🆕
| 文档 | 说明 | 优先级 |
|------|------|--------|
| **[PERSISTENCE_DESIGN_PLAN.md](PERSISTENCE_DESIGN_PLAN.md)** | 📘 持久化功能节点设计方案（完整版） | ⭐⭐⭐⭐⭐ |
| **[PERSISTENCE_DESIGN_SUMMARY.md](PERSISTENCE_DESIGN_SUMMARY.md)** | 📊 设计方案快速参考（精简版） | ⭐⭐⭐⭐⭐ |
| **[PERSISTENCE_IMPLEMENTATION_CHECKLIST.md](PERSISTENCE_IMPLEMENTATION_CHECKLIST.md)** | ✅ 实施检查清单 | ⭐⭐⭐⭐ |

**快速开始**:
```bash
# 1. 阅读入口文档
cat PERSISTENCE_README.md

# 2. 初始化数据库
cd mcp-router-v3/database && ./setup.sh your_password

# 3. 查看完整指南
cat PERSISTENCE_GUIDE.md
```

---

### 📦 项目主文档

| 文档 | 说明 |
|------|------|
| **[README.md](../README.md)** | 项目总览 |
| **[mcp-router-v3/readme.md](../mcp-router-v3/readme.md)** | Router V3 说明 |

---

## 🔧 功能文档

### Web UI 集成
- [mcp-router-v3/WEB_UI_INTEGRATION_SUMMARY.md](../mcp-router-v3/WEB_UI_INTEGRATION_SUMMARY.md)

### 日志增强
- [mcp-router-v3/LOGGING_ENHANCEMENT_SUMMARY.md](../mcp-router-v3/LOGGING_ENHANCEMENT_SUMMARY.md)
- [mcp-router-v3/DEBUG_GUIDE.md](../mcp-router-v3/DEBUG_GUIDE.md)

### MySQL集成（历史文档）
- [mcp-router-v3/MYSQL_INTEGRATION_SUMMARY.md](./mcp-router-v3/MYSQL_INTEGRATION_SUMMARY.md) ⚠️ 已过时
- [mcp-router-v3/MYSQL_MYBATIS_INTEGRATION_SUMMARY.md](./mcp-router-v3/MYSQL_MYBATIS_INTEGRATION_SUMMARY.md) ⚠️ 已过时

> **注意**: MySQL相关功能请使用 **PERSISTENCE_GUIDE.md**，上述文档已废弃。

---

## 📖 详细文档

### API 文档
- [docs-250812/MCP_BRIDGE_API_REFERENCE.md](../docs-250812/MCP_BRIDGE_API_REFERENCE.md)
- [docs-250812/MCP_BRIDGE_CONFIG_MANUAL.md](../docs-250812/MCP_BRIDGE_CONFIG_MANUAL.md)

### 性能与故障排查
- [docs-250812/MCP_BRIDGE_PERFORMANCE_GUIDE.md](../docs-250812/MCP_BRIDGE_PERFORMANCE_GUIDE.md)
- [docs-250812/MCP_BRIDGE_TROUBLESHOOTING_GUIDE.md](../docs-250812/MCP_BRIDGE_TROUBLESHOOTING_GUIDE.md)

### 集成指南
- [docs-250812/DIFY_INTEGRATION_GUIDE.md](../docs-250812/DIFY_INTEGRATION_GUIDE.md)

---

## 🧪 测试相关

### 测试文档
- [docs/MCP_STANDARD_PROTOCOL_TESTS.md](../docs/MCP_STANDARD_PROTOCOL_TESTS.md)
- [docs/MCP_STANDARD_PROTOCOL_TEST_FIX_SUMMARY.md](../docs/MCP_STANDARD_PROTOCOL_TEST_FIX_SUMMARY.md)

### 测试脚本
```
testScript/
├── verify-mcp-router.sh           # MCP Router 验证
├── verify-optimization.sh         # 优化验证
├── verify-sse-connection.sh       # SSE 连接验证
└── test-mcp-integration.sh        # 集成测试
```

---

## 🏗️ 架构文档

### 架构分析
- [docs/spring2.7-250806/MCP_PROJECT_ARCHITECTURE.md](../docs/spring2.7-250806/MCP_PROJECT_ARCHITECTURE.md)
- [docs/spring2.7-250806/SPRING_AI_ALIBABA_MCP_ANALYSIS.md](../docs/spring2.7-250806/SPRING_AI_ALIBABA_MCP_ANALYSIS.md)

### Spring Boot 2.7 修复
- [docs/spring2.7-250805/FINAL_SUCCESS_REPORT.md](../docs/spring2.7-250805/FINAL_SUCCESS_REPORT.md)
- [docs/spring2.7-250805/FIX_SUMMARY.md](../docs/spring2.7-250805/FIX_SUMMARY.md)

---

## 📂 项目结构

```
mcp-router-sse-parent/
│
├── 📋 核心文档
│   ├── PERSISTENCE_README.md          ← 持久化入口（从这里开始）
│   ├── PERSISTENCE_GUIDE.md           ← 持久化完整指南
│   ├── README.md                      ← 项目总览
│   └── DOCS_INDEX.md                  ← 本文档
│
├── 📦 主项目: mcp-router-v3/
│   ├── database/
│   │   ├── schema.sql                 ← 数据库Schema（v2.0优化版）
│   │   └── setup.sh                   ← 一键安装脚本
│   ├── src/                           ← 源代码
│   ├── scripts/                       ← 工具脚本
│   └── docs/                          ← 项目文档
│
├── 📚 文档目录
│   ├── docs/                          ← 早期文档
│   └── docs-250812/                   ← 最新文档
│
├── 🧪 测试项目
│   ├── mcp-server-v3/                 ← MCP Server v3
│   ├── mcp-server-v4/                 ← MCP Server v4
│   ├── mcp-server-v5/                 ← MCP Server v5
│   ├── mcp-server-v6/                 ← MCP Server v6
│   ├── mcp-client/                    ← MCP Client
│   └── testScript/                    ← 测试脚本
│
└── 🔧 依赖项目
    ├── spring-ai-alibaba/             ← Spring AI Alibaba
    ├── spring-cloud-alibaba/          ← Spring Cloud Alibaba
    ├── dubbo-*/                       ← Dubbo 测试项目
    └── zookeeper/                     ← Zookeeper 配置
```

---

## 🎯 快速导航

### 我想...

| 需求 | 文档 |
|------|------|
| **集成MySQL持久化** | [PERSISTENCE_README.md](PERSISTENCE_README.md) |
| **了解架构设计** | [PERSISTENCE_GUIDE.md](PERSISTENCE_GUIDE.md) |
| **排查问题** | [PERSISTENCE_GUIDE.md#故障排查](PERSISTENCE_GUIDE.md#故障排查) |
| **性能调优** | [PERSISTENCE_GUIDE.md#性能调优](PERSISTENCE_GUIDE.md#性能调优) |
| **查看API** | [docs-250812/MCP_BRIDGE_API_REFERENCE.md](../docs-250812/MCP_BRIDGE_API_REFERENCE.md) |
| **集成到Dify** | [docs-250812/DIFY_INTEGRATION_GUIDE.md](../docs-250812/DIFY_INTEGRATION_GUIDE.md) |
| **运行测试** | [testScript/verify-mcp-router.sh](../testScript/verify-mcp-router.sh) |

---

## ⚠️ 文档状态说明

| 状态 | 说明 |
|------|------|
| ✅ **当前** | 最新版本，推荐使用 |
| 📝 **维护中** | 持续更新 |
| ⚠️ **已过时** | 已有新版本替代 |
| 🗄️ **归档** | 历史参考 |

### 持久化相关文档状态

| 文档 | 状态 |
|------|------|
| **PERSISTENCE_GUIDE.md** | ✅ 当前（v2.0生产级） |
| **database/schema.sql** | ✅ 当前（v2.0优化版） |
| MYSQL_INTEGRATION_SUMMARY.md | ⚠️ 已过时 |
| MYSQL_MYBATIS_INTEGRATION_SUMMARY.md | ⚠️ 已过时 |
| MCP_ROUTER_V3_PERSISTENCE_ANALYSIS.md | 🗄️ 已删除（初版） |
| OPTIMIZATION_SUMMARY.md | 🗄️ 已删除（对比文档） |
| QUICK_START_GUIDE.md | 🗄️ 已删除（已整合） |

---

## 📝 文档原则

1. **最小必要性** - 一个功能只有一份主文档
2. **及时更新** - 功能变更后立即更新文档
3. **删除冗余** - 有新版本后删除旧文档
4. **清晰入口** - 提供明确的文档索引

---

## 🤝 贡献

如果发现文档问题：
1. 查看是否有更新版本
2. 检查文档状态（是否已过时）
3. 提交Issue或PR

---

**文档维护者**: MCP Router V3 Team  
**最后更新**: 2025-03-01

