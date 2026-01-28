# Architecture Decision Records (ADR)

> 记录项目中所有重要的架构决策

---

## 📋 ADR 列表

| # | 标题 | 状态 | 日期 | 作者 |
|---|------|------|------|------|
| [001](001-streamable-session-dual-transmission.md) | Streamable 协议双重 Session ID 传递机制 | ✅ Accepted | 2026-01-28 | AI Assistant |

---

## 🔍 状态说明

- ✅ **Accepted** - 已接受并实施
- 🚧 **Proposed** - 已提出，待评审
- ⏸️ **Deprecated** - 已弃用
- ❌ **Rejected** - 已拒绝
- 🔄 **Superseded** - 已被新决策取代

---

## 📝 ADR 模板

创建新 ADR 时，请使用以下模板：

```markdown
# ADR-XXX: [简短标题]

## Status
[Proposed/Accepted/Deprecated/Rejected/Superseded]

## Context
描述问题和背景

## Decision
描述决策内容

## Alternatives Considered
列出考虑过的替代方案

## Consequences
### Positive
- 优点1
- 优点2

### Negative
- 缺点1
- 缺点2

## Implementation
- 代码位置
- 相关文档
- 测试验证
```

---

## 🔗 相关资源

- [ADR 最佳实践](../reference/best-practices-traceability.md#adr--arc42)
-  [功能索引](../features/README.md)
- [追溯矩阵](../traceability/)

---

## 💡 使用指南

### 何时创建 ADR?

创建 ADR 的时机：
- ✅ 架构设计决策（如选择技术栈、设计模式）
- ✅ 重要的技术选型（如数据库、消息队列）
- ✅ 影响多个模块的设计决策
- ✅ 有重要权衡的决策

不需要创建 ADR的情况：
- ❌ 小的代码重构
- ❌ Bug 修复（除非涉及架构变更）
- ❌ 配置调整

### 如何创建 ADR?

1. 复制模板
2. 为 ADR 分配编号（递增）
3. 填写各个部分
4. 链接相关代码、测试、文档
5. 提交 PR 并请求评审
6. 更新此索引

---

**维护者**: 架构团队  
**最后更新**: 2026-01-28  
**ADR 数量**: 1
