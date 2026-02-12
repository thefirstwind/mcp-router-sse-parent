# Bug修复归档

> 记录所有已修复的Bug，包括根本原因分析、修复方案和验证结果

---

## 目录结构

```
docs/explanations/bug-fixes/
├── README.md (本文档)
└── REQ-YYYYMMDD-XXX-*.md (Bug修复详细文档)
```

---

## 已修复的Bug

### REQ-20260211-001: getUserById 泛化调用参数类型错误

- **文档**: [REQ-20260211-001-getUserById-type-fix.md](./REQ-20260211-001-getUserById-type-fix.md)
- **报告时间**: 2026-02-11 18:35
- **修复时间**: 2026-02-11 18:40
- **优先级**: High
- **状态**: ✅ 已修复，等待测试验证

**问题**:
```
SSE连接后调用 getUserById 报错: NoSuchMethodException(int)
```

**根本原因**:
- Dubbo泛化调用类型严格匹配
- `Long` 参数被错误转换为 `long` 基本类型

**修复方案**:
- 修改 `ParameterConverter.convertPrimitive()`
- 确保 `java.lang.Long` 返回 `Long.valueOf()` 而非 `longValue()`

**影响文件**:
- `ParameterConverter.java` (+60/-40 行)
- `testScript/test-getUserById-fix.sh` (新建)

**追溯链**: ✅ 完整
- Requirement: docs/requirements/REQ-20260211-001.md
- Code: ParameterConverter.java
- Test: test-getUserById-fix.sh
- Doc: 本文档

---

## Bug分类统计

| 类型 | 数量 | 已修复 | 进行中 |
|------|------|--------|--------|
| 参数类型问题 | 1 | 1 | 0 |
| 连接超时 | 0 | 0 | 0 |
| 配置错误 | 0 | 0 | 0 |
| **总计** | **1** | **1** | **0** |

---

## 添加新的Bug修复文档

### 命名规范

```
REQ-YYYYMMDD-XXX-<short-description>.md
```

例如：
- `REQ-20260211-001-getUserById-type-fix.md`
- `REQ-20260212-002-connection-timeout-fix.md`

### 文档模板

```markdown
# Bug修复总结: <标题>

> **需求ID**: REQ-YYYYMMDD-XXX  
> **类型**: Bug Report  
> **优先级**: High/Medium/Low  
> **状态**: 已修复/进行中  
> **创建时间**: YYYY-MM-DD HH:MM  
> **修复时间**: YYYY-MM-DD HH:MM

## 问题描述
用户报告的问题...

## 根本原因分析
技术分析...

## 修复方案
代码改动...

## 测试验证
测试步骤和结果...

## 追溯链
- Requirement: ...
- Code: ...
- Test: ...
- Doc: ...

## 经验总结
技术要点和防止措施...
```

---

## 相关文档

- [需求管理](../../requirements/)
- [架构决策记录](../../adr/)
- [文档追溯系统](../../../DOCUMENTATION_TRACEABILITY_SYSTEM.md)
- [自动化规则](../../../.agent/rules/automation-rules.md)

---

**维护者**: AI Agent System  
**最后更新**: 2026-02-11 18:44
