# Features Index

> 项目功能索引 - 快速查找所有功能的文档、代码、测试

---

## 📚 功能列表

### 🔄 Streamable Session Management (2026-01-28)

**状态**: ✅ 已完成  
**版本**: 1.0  
**分支**: bugfix/fix-streamable-session-management

**快速链接**:
- 📖 [功能文档](streamable-session-management.md)
- 💻 [代码位置](../../mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java#L329-L362)
- 🧪 [测试脚本](../../test_streamable_comprehensive.sh)
- 📊 [测试报告](../../TEST_VERIFICATION_REPORT.md)

**核心修改**:
- 在 NDJSON 流开头添加 session 消息
- 增强 sessionId 解析和日志
- 双重传递机制（响应头 + 消息体）

**关键字**: `streamable`, `session`, `ndjson`, `mcp-session-id`

---

## 📋 如何使用本索引

### 查找功能
1. 按日期查找 - 最近的功能在最上面
2. 按关键字搜索 - 使用 Ctrl+F 搜索关键词
3. 按状态筛选 - ✅ 已完成 / 🚧 开发中 / 📝 计划中

### 添加新功能
创建新功能时，请按以下模板添加:

```markdown
### 🆕 Feature Name (YYYY-MM-DD)

**状态**: 🚧 开发中  
**版本**: 0.1  
**分支**: feature/xxx

**快速链接**:
- 📖 [功能文档](feature-name.md)
- 💻 [代码位置](../../path/to/code)
- 🧪 [测试脚本](../../test_feature.sh)
- 📊 [测试报告](../../TEST_REPORT.md)

**核心修改**:
- 简要描述主要修改

**关键字**: `keyword1`, `keyword2`
```

---

## 🔗 相关资源

- [如何添加新功能](../how-to-guides/add-feature.md)
- [代码审查流程](../../.agent/workflows/review.md)
- [测试指南](../how-to-guides/testing-guide.md)
- [文档规范](../reference/documentation-standards.md)

---

**维护**: 每次添加新功能时更新此索引  
**最后更新**: 2026-01-28
