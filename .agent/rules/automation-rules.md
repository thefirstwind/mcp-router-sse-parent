# Agent 自动化规则

> 定义 Agent 在需求生命周期管理中的自动化行为规则

---

## 📋 核心规则

### 规则 1: 自动需求分类

当用户提出问题或请求时，Agent **必须自动**进行分类：

```javascript
function classifyRequest(userMessage) {
  const patterns = {
    pure_question: [
      /^(什么|如何|怎么|为什么|能否解释)/,
      /\?(.*?)$/,
      /是什么/,
      /有什么区别/
    ],
    documentation_request: [
      /需要.*文档/,
      /写.*文档/,
      /创建.*文档/,
      /更新.*文档/,
      /说明书/,
      /操作指南/
    ],
    bug_report: [
      /报错/,
      /失败/,
      /不工作/,
      /异常/,
      /bug/i,
      /错误/,
      /崩溃/
    ],
    feature_request: [
      /新增/,
      /添加/,
      /实现/,
      /支持.*功能/,
      /能不能.*实现/,
      /想要.*功能/
    ],
    enhancement: [
      /优化/,
      /改进/,
      /提升/,
      /重构/,
      /better/i,
      /improve/i
    ]
  };
  
  for (const [type, regexList] of Object.entries(patterns)) {
    if (regexList.some(regex => regex.test(userMessage))) {
      return type;
    }
  }
  
  return 'uncertain';  // 需要询问用户
}
```

### 规则 2: 自动生成需求 ID

需求 ID 格式: `REQ-{YYYYMMDD}-{序号}`

```javascript
function generateRequirementId() {
  const date = new Date();
  const dateStr = date.toISOString().slice(0,10).replace(/-/g, '');
  
  // 查找当天最大序号
  const existingReqs = findRequirementsForDate(dateStr);
  const maxSeq = Math.max(...existingReqs.map(r => r.sequence), 0);
  
  const sequence = String(maxSeq + 1).padStart(3, '0');
  return `REQ-${dateStr}-${sequence}`;
}
```

### 规则 3: 自动追溯标记

在以下情况**必须**添加追溯标记：

#### 3.1 Java 代码

```java
/**
 * {功能描述}
 * 
 * @traceability
 *   - Requirement: REQ-YYYYMMDD-XXX
 *   - Design: {ADR编号或设计文档路径}
 *   - Test: {测试文件路径}
 *   - Documentation: {文档路径}
 * 
 * @author {作者}
 * @since {版本}
 */
```

#### 3.2 测试脚本

```bash
#!/bin/bash
# {脚本描述}
#
# @traceability
#   - Requirement: REQ-YYYYMMDD-XXX
#   - Design: {设计文档}
#   - Code: {相关代码文件}
#   - Documentation: {文档路径}
```

#### 3.3 文档

```markdown
---
status: active
created: YYYY-MM-DD
requirements: [REQ-YYYYMMDD-XXX]
---

## Traceability
- **Requirement**: REQ-YYYYMMDD-XXX
- **Design**: [ADR-XXX](path/to/adr.md)
- **Code**: file1.java, file2.java
- **Test**: test1.java, test2.sh
```

### 规则 4: 自动运行检查

在以下时机**自动运行**追溯检查（无需用户确认）：

```yaml
triggers:
  - before_pr_creation
  - after_doc_update
  - on_agent_startup
  - on_conversation_start
```

执行命令：
```bash
// turbo
./scripts/maintenance/check-traceability.sh
```

### 规则 5: 自动状态管理

Agent 维护需求状态文件: `.agent/state/requirements.json`

**状态转换规则**：

```
draft → in_progress → pr_created → merged → completed
  ↓         ↓            ↓            ↓          ↓
自动       代码编写     PR创建     合并检测   自动关闭
```

**自动转换时机**：

- `draft → in_progress`: 开始编写代码时
- `in_progress → pr_created`: 用户确认创建PR后
- `pr_created → merged`: 每次对话开始时检测
- `merged → completed`: 检测到分支已合并时

### 规则 6: 自动检测合并状态

每次对话开始时，Agent **必须**：

```javascript
async function checkRequirementsStatus() {
  const requirements = loadRequirements('.agent/state/requirements.json');
  
  for (const req of requirements) {
    if (req.status === 'pr_created' || req.status === 'in_progress') {
      const isMerged = await checkIfMergedToMain(req.branch);
      
      if (isMerged) {
        // 自动关闭需求
        await closeRequirement(req.id);
        
        // 通知用户
        notifyUser(`✅ 需求 ${req.id} 已自动关闭（已合并到主干）`);
      }
    }
  }
}
```

### 规则 7: 分支命名规范

Agent **必须**遵循的分支命名：

```
feature/REQ-{YYYYMMDD}-{序号}-{简短描述}
bugfix/REQ-{YYYYMMDD}-{序号}-{简短描述}
enhancement/REQ-{YYYYMMDD}-{序号}-{简短描述}
docs/REQ-{YYYYMMDD}-{序号}-{简短描述}
```

示例：
- `feature/REQ-20260211-001-websocket-support`
- `bugfix/REQ-20260211-002-fix-sse-timeout`

### 规则 8: 提交信息规范

```
{type}({scope}): {简短描述}

{详细描述}

Traceability: REQ-{YYYYMMDD}-{序号}
{其他元信息}
```

Type 枚举：
- `feat`: 新功能
- `fix`: Bug修复
- `docs`: 文档
- `refactor`: 重构
- `test`: 测试
- `chore`: 其他

---

## 🤖 自动化决策树

### 决策树 1: 用户请求分类

```
用户输入
    │
    ├─ 包含"如何"/"什么"/"为什么" → pure_question
    │   └─ 直接回答 + 提供文档链接
    │
    ├─ 包含"文档"/"说明"/"指南" → documentation_request
    │   ├─ 检查是否存在
    │   ├─ 存在 → 更新
    │   └─ 不存在 → 创建
    │
    ├─ 包含"报错"/"失败"/"bug" → bug_report
    │   └─ 创建需求 → 分析 → 修复 → 测试 → PR
    │
    ├─ 包含"新增"/"添加"/"实现" → feature_request
    │   └─ 创建需求 → 设计 → 实现 → 测试 → 文档 → PR
    │
    ├─ 包含"优化"/"改进" → enhancement
    │   ├─ 分析是否需要代码修改
    │   ├─ 需要 → 同 feature_request
    │   └─ 不需要 → 配置调整或文档建议
    │
    └─ 不确定 → uncertain
        └─ 询问用户:"这是一个问题还是功能请求？"
```

### 决策树 2: 是否需要创建 ADR

```
架构级变更判断
    │
    ├─ 新增重要组件？ → YES → 创建 ADR
    ├─ 修改核心架构？ → YES → 创建 ADR
    ├─ 技术选型变更？ → YES → 创建 ADR
    ├─ 影响多个模块？ → YES → 创建 ADR
    ├─ 有重要权衡？ → YES → 创建 ADR
    └─ 其他 → NO → 只更新实施文档
```

### 决策树 3: 文档类型选择

```
需要创建文档
    │
    ├─ 教学性质（新手学习）→ Tutorial
    │   └─ docs/tutorials/{主题}.md
    │
    ├─ 任务导向（完成特定任务）→ How-To Guide
    │   └─ docs/how-to-guides/{任务}.md
    │
    ├─ 概念解释（理解原理）→ Explanation
    │   └─ docs/explanations/{概念}.md
    │
    └─ 信息查询（查找参数）→ Reference
        └─ docs/reference/{类别}.md
```

---

## 🚫 Agent 不应该自动执行的操作

以下操作**必须**获得用户确认：

1. ❌ **Git push**
   - 理由：用户可能想先检查代码

2. ❌ **创建 GitHub PR**
   - 理由：用户可能想调整PR描述

3. ❌ **合并 PR**
   - 理由：需要Code Review

4. ❌ **删除分支**
   - 理由：用户可能还需要

5. ❌ **修改主干代码**
   - 理由：高风险操作

6. ❌ **删除文件**
   - 理由：数据安全

7. ❌ **重大架构变更**
   - 理由：需要团队讨论

---

## ✅ Agent 应该自动执行的操作

以下操作**可以且应该**自动执行：

1. ✅ **分类用户请求**
   - 基于模式匹配

2. ✅ **创建需求文档**
   - 标准化模板

3. ✅ **生成需求 ID**
   - 遵循命名规范

4. ✅ **添加追溯标记**
   - 必须的元数据

5. ✅ **运行检查脚本**
   - 无副作用

6. ✅ **生成实施方案**
   - 帮助用户决策

7. ✅ **编写代码**
   - 在功能分支上

8. ✅ **编写测试**
   - 确保质量

9. ✅ **生成文档**
   - 保持同步

10. ✅ **检测合并状态**
    - 自动化监控

11. ✅ **关闭已完成需求**
    - 自动化管理

12. ✅ **更新 CHANGELOG**
    - 发布管理

---

## 📝 模板管理

Agent 应使用以下模板：

### 需求文档模板

位置：`.agent/templates/requirement.md`

```markdown
---
id: {REQ_ID}
status: draft
type: {TYPE}
priority: {PRIORITY}
created: {DATE}
assignee: auto
---

# {TITLE}

## 需求来源
{USER_ORIGINAL_REQUEST}

## 需求分析
{AGENT_ANALYSIS}

## 验收标准
- [ ] 标准1
- [ ] 标准2

## 影响范围
- 模块: {MODULES}
- 文件: {FILES}

## 实施计划
1. {STEP1}
2. {STEP2}

## 追溯链
- Design: (待创建)
- Code: (待实现)
- Test: (待编写)
- Documentation: (待更新)
```

### ADR 模板

位置：`.agent/templates/adr.md`

```markdown
# ADR-{NUMBER}: {TITLE}

## Status
Proposed

## Context
{PROBLEM_DESCRIPTION}

## Decision
{DECISION_DESCRIPTION}

## Alternatives Considered
1. {ALTERNATIVE_1}
2. {ALTERNATIVE_2}

## Consequences
### Positive
- {BENEFIT_1}

### Negative
- {DRAWBACK_1}

## Traceability
- Requirement: {REQ_ID}
- Implementation: (待实现)
- Test: (待编写)
- Documentation: (待更新)
```

---

## 🔔 通知规则

Agent 应在以下情况通知用户：

### 🟢 信息通知（INFO）

- ✅ 需求已创建
- ✅ 实施方案已生成
- ✅ 代码已编写
- ✅ 测试已通过
- ✅ 文档已生成

### 🟡 警告通知（WARNING）

- ⚠️ 追溯检查发现问题
- ⚠️ 文档链接损坏
- ⚠️ 测试覆盖率不足
- ⚠️ 代码风格问题

### 🔴 错误通知（ERROR）

- ❌ 测试失败
- ❌ 构建失败
- ❌ 追溯链不完整
- ❌ 必需文件缺失

### ✅ 成功通知（SUCCESS）

- 🎉 需求已完成并合并
- 🎉 所有检查通过
- 🎉 文档已发布

---

## 📊 状态文件格式

### requirements.json

```json
{
  "version": "1.0",
  "last_updated": "2026-02-11T18:00:00Z",
  "requirements": {
    "REQ-20260211-001": {
      "id": "REQ-20260211-001",
      "title": "WebSocket传输支持",
      "type": "feature_request",
      "status": "in_progress",
      "priority": "high",
      "created": "2026-02-11T10:00:00Z",
      "updated": "2026-02-11T18:00:00Z",
      "branch": "feature/REQ-20260211-001-websocket-support",
      "pr": null,
      "merged": false,
      "completed": null,
      "traceability": {
        "requirement": "docs/requirements/REQ-20260211-001.md",
        "design": "docs/adr/005-websocket-transport.md",
        "code": [
          "src/main/java/.../WebSocketHandler.java",
          "src/main/java/.../McpRouterServerConfig.java"
        ],
        "test": [
          "src/test/java/.../WebSocketHandlerTest.java",
          "testScript/test_websocket_connection.sh"
        ],
        "documentation": [
          "docs/features/websocket-transport.md",
          "docs/reference/configuration.md"
        ]
      },
      "verification": {
        "traceability_complete": true,
        "tests_passing": true,
        "docs_updated": true,
        "links_valid": true
      }
    }
  }
}
```

---

## 🔄 Agent 初始化检查清单

每次对话开始时，Agent **必须**执行：

```javascript
async function initializeAgent() {
  // 1. 加载需求状态
  const requirements = await loadRequirements();
  
  // 2. 检查是否有待处理的需求
  const pendingRequirements = requirements.filter(
    r => r.status !== 'completed' && r.status !== 'cancelled'
  );
  
  // 3. 对每个待处理需求，检查合并状态
  for (const req of pendingRequirements) {
    if (req.branch) {
      const isMerged = await checkIfMergedToMain(req.branch);
      
      if (isMerged && req.status !== 'completed') {
        await closeRequirement(req.id);
        console.log(`✅ 自动关闭需求: ${req.id}`);
      }
    }
  }
  
  // 4. 生成待办事项摘要
  if (pendingRequirements.length > 0) {
    console.log(`📋 当前有 ${pendingRequirements.length} 个进行中的需求`);
  }
}
```

---

**维护者**: AI Agent System  
**最后更新**: 2026-02-11  
**版本**: 1.0
