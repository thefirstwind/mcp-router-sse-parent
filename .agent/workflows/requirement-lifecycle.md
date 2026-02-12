---
description: 需求生命周期自动化管理工作流
---

# 需求生命周期自动化管理工作流

> **目标**: 自动化需求从创建到关闭的全生命周期管理，无需人工干预

---

## 工作流概述

```
用户提出问题/需求
    ↓
Agent 自动分类
    ↓
判断是否需要代码修改
    ├─ 需要 → 进入开发流程
    │   ├─ 创建需求文档 (REQ-XXX)
    │   ├─ 生成实现方案
    │   ├─ 编写代码
    │   ├─ 编写测试
    │   ├─ 生成文档
    │   ├─ 创建 PR
    │   └─ 等待合并
    └─ 不需要 → 直接回答问题
        └─ 生成文档（如需要）

合并到主干后
    ↓
自动关闭需求
    ├─ 更新追溯链
    ├─ 生成 CHANGELOG
    └─ 标记需求为已完成
```

---

## 步骤详解

### 步骤 1: 需求接收和分类

当用户提出问题时，Agent 自动分析并分类：

**分类标准**:

```javascript
{
  "pure_question": "纯问题，无需代码修改",
  "documentation_request": "文档请求，需要创建/更新文档",
  "bug_report": "Bug报告，需要修复代码",
  "feature_request": "功能请求，需要新增代码",
  "enhancement": "改进建议，可能需要代码修改"
}
```

**Agent 判断依据**:
- 包含"如何"、"怎么"、"为什么" → 纯问题
- 包含"文档"、"说明"、"指南" → 文档请求
- 包含"报错"、"失败"、"不工作" → Bug报告
- 包含"新增"、"添加"、"实现" → 功能请求
- 包含"优化"、"改进"、"提升" → 改进建议

### 步骤 2: 需求文档生成

如果需要代码修改，Agent 自动创建需求文档：

**位置**: `docs/requirements/REQ-{YYYYMMDD}-{序号}.md`

**模板**:

```markdown
---
id: REQ-20260211-001
status: draft
type: feature_request | bug_report | enhancement
priority: high | medium | low
created: 2026-02-11
assignee: auto
---

# {需求标题}

## 需求来源
{用户原始问题}

## 需求分析
{Agent的分析}

## 验收标准
- [ ] 标准1
- [ ] 标准2
- [ ] 标准3

## 影响范围
- 模块: {affected_modules}
- 文件: {affected_files}

## 实施计划
1. 设计方案
2. 代码实现
3. 测试验证
4. 文档更新

## 追溯链
- Design: (待创建)
- Code: (待实现)
- Test: (待编写)
- Documentation: (待更新)
```

### 步骤 3: 实施方案生成

Agent 自动生成实施方案：

**如果是架构级变更 → 创建 ADR**:
- 位置: `docs/adr/{序号}-{标题}.md`
- 包含: Context, Decision, Alternatives, Consequences
- 追溯: 关联到 REQ-XXX

**如果是代码级变更 → 创建实施计划**:
- 位置: 内嵌在需求文档中
- 包含: 文件清单、修改点、测试策略

### 步骤 4: 代码实现

Agent 自动进行代码修改：

**必须遵循的规则**:

1. **添加追溯标记**:
```java
/**
 * {功能描述}
 * 
 * @traceability
 *   - Requirement: REQ-20260211-001
 *   - Design: ADR-XXX (如有)
 *   - Test: {测试文件}
 *   - Documentation: {文档位置}
 */
```

2. **遵循现有架构**:
- 不破坏现有设计模式
- 保持代码风格一致
- 添加必要的日志和注释

3. **错误处理**:
- 添加适当的异常处理
- 记录错误日志
- 提供有意义的错误消息

### 步骤 5: 测试编写

Agent 自动编写测试：

**测试层级**:

1. **单元测试** (必须):
```java
/**
 * @traceability REQ-20260211-001
 */
@Test
void testNewFeature() {
    // 测试代码
}
```

2. **集成测试** (视情况):
- 如果涉及多个模块交互
- 创建集成测试

3. **端到端测试** (视情况):
- 如果是用户可见功能
- 添加 Shell 测试脚本

**测试脚本包含追溯信息**:
```bash
#!/bin/bash
# @traceability
#   - Requirement: REQ-20260211-001
#   - Code: {相关代码文件}
```

### 步骤 6: 文档生成

Agent 自动生成/更新文档：

**文档类型判断**:

```
if 新功能:
    创建 docs/features/{功能名}.md
    更新 docs/reference/api/ (如有API)
    
if 操作变更:
    更新 docs/how-to-guides/{相关指南}.md
    
if 架构变更:
    更新 docs/explanations/architecture.md
    创建 ADR
    
if 配置变更:
    更新 docs/reference/configuration.md
```

**文档必须包含**:
- Traceability 章节（关联需求、设计、代码、测试）
- Frontmatter 元数据
- 实际示例

### 步骤 7: Git 操作

Agent 自动处理 Git 操作：

**分支命名规范**:
```
feature/REQ-20260211-001-{简短描述}
bugfix/REQ-20260211-001-{简短描述}
```

**提交信息规范**:
```
feat(module): 简短描述

详细描述

Traceability: REQ-20260211-001
```

**不自动执行的操作**:
- ❌ 不自动 push
- ❌ 不自动创建 PR
- ❌ 不自动合并

**需要用户确认**:
```
Agent: "已完成以下工作：
  - 创建需求文档: REQ-20260211-001
  - 修改代码: {文件列表}
  - 编写测试: {测试文件}
  - 更新文档: {文档文件}
  
是否要创建 Pull Request？
  选项 1: 创建 PR (需要您手动push)
  选项 2: 继续修改
  选项 3: 放弃更改"
```

### 步骤 8: 追溯验证

创建 PR 前，Agent 自动运行追溯检查：

```bash
// turbo
# 运行追溯检查
./scripts/maintenance/check-traceability.sh
```

**检查项**:
- [x] 需求文档存在
- [x] 设计文档存在（如需要）
- [x] 代码包含 @traceability
- [x] 测试覆盖新功能
- [x] 用户文档已更新
- [x] 所有链接有效

### 步骤 9: 合并检测

Agent 定期检查分支状态：

**检查频率**: 每次对话开始时

**检查逻辑**:
```python
def check_requirement_status(req_id):
    # 1. 查找分支
    branch = find_branch_by_requirement(req_id)
    if not branch:
        return "no_branch"
    
    # 2. 检查是否已合并到 main
    if is_merged_to_main(branch):
        return "merged"
    
    # 3. 检查是否有 PR
    pr = find_pr_by_branch(branch)
    if pr:
        return f"pr_open: {pr.status}"
    
    return "branch_exists"
```

### 步骤 10: 自动关闭需求

当检测到需求已合并到主干，Agent 自动：

1. **更新需求文档**:
```markdown
---
id: REQ-20260211-001
status: completed  # draft → completed
completed_date: 2026-02-11
---
```

2. **生成完成报告**:
```markdown
## 需求完成报告

### 实施总结
- 代码变更: {文件数} 个文件，{行数} 行代码
- 测试覆盖: {覆盖率}%
- 文档更新: {文档数} 个文档

### 追溯链验证
- [x] 设计文档: docs/adr/XXX.md
- [x] 代码实现: {文件列表}
- [x] 测试验证: {测试文件}
- [x] 用户文档: {文档文件}

### 合并信息
- PR: #{pr_number}
- 合并时间: {merge_time}
- 合并到: main
```

3. **更新 CHANGELOG**:
```markdown
## [Unreleased]

### Added
- REQ-20260211-001: {需求标题} (#pr_number)
```

4. **通知用户**:
```
Agent: "✅ 需求 REQ-20260211-001 已完成并合并到主干

完成信息:
- 合并时间: 2026-02-11 18:30
- PR: #123
- 追溯链: 完整

需求现已关闭。"
```

---

## 特殊情况处理

### 情况 1: 纯问题（无需代码修改）

```
用户: "Streamable 协议是什么？"

Agent 判断: pure_question
    ↓
直接回答 + 提供相关文档链接
    ↓
询问是否需要创建教程文档
    ├─ 是 → 创建 docs/tutorials/{主题}.md
    └─ 否 → 完成
```

### 情况 2: 文档请求

```
用户: "需要一份如何添加MCP Server的文档"

Agent 判断: documentation_request
    ↓
检查是否已存在相关文档
    ├─ 存在 → 更新现有文档
    └─ 不存在 → 创建新文档
        ↓
    选择文档类型 (Tutorial/How-To/Explanation/Reference)
        ↓
    使用对应模板创建
        ↓
    添加到导航 (docs/README.md)
```

### 情况 3: Bug 报告

```
用户: "SSE 连接总是超时"

Agent 判断: bug_report
    ↓
创建需求: REQ-20260211-001 (type: bug_report)
    ↓
分析根本原因
    ├─ 查看相关代码
    ├─ 查看历史 Issues
    └─ 查看设计文档
    ↓
提出修复方案
    ↓
实施修复（代码 + 测试 + 文档）
    ↓
验证修复
    ↓
创建 PR
```

### 情况 4: 改进建议（不确定是否需要代码修改）

```
用户: "能不能提升心跳检测的性能？"

Agent 判断: enhancement (uncertain)
    ↓
先进行分析
    ├─ 查看当前实现
    ├─ 分析性能瓶颈
    └─ 评估改进方案
    ↓
询问用户确认
    ├─ 方案A: 代码优化（需要修改代码）
    ├─ 方案B: 配置调整（无需修改代码）
    └─ 方案C: 暂不实施
    ↓
根据用户选择执行
```

---

## 自动化规则总结

### Agent 自动执行（无需确认）:

✅ 分类用户请求  
✅ 分析需求  
✅ 创建需求文档  
✅ 生成实施方案  
✅ 编写代码  
✅ 编写测试  
✅ 生成文档  
✅ 运行追溯检查  
✅ 检测合并状态  
✅ 关闭已完成的需求  

### 需要用户确认:

⚠️ 是否创建 PR  
⚠️ 重大架构变更  
⚠️ 不确定的改进方案  

### Agent 不执行:

❌ Git push  
❌ 创建 GitHub PR  
❌ 合并 PR  
❌ 删除分支  

---

## 示例对话流程

### 示例 1: 功能请求

```
用户: 我想添加对WebSocket传输的支持

Agent: 
  [自动分析]
  类型: feature_request
  影响: 需要代码修改
  
  [创建需求文档]
  已创建: docs/requirements/REQ-20260211-001.md
  
  [生成实施方案]
  需要修改的文件:
  - McpRouterServerConfig.java
  - WebSocketHandler.java (新增)
  - application.yml
  
  需要的测试:
  - WebSocketHandlerTest.java
  - test_websocket_connection.sh
  
  需要的文档:
  - docs/features/websocket-transport.md
  - docs/reference/configuration.md (更新)
  
  是否开始实施？
  [1] 是，开始实施
  [2] 先看看详细方案
  [3] 暂不实施

用户: 1

Agent:
  [实施中...]
  ✓ 创建 WebSocketHandler.java
  ✓ 修改 McpRouterServerConfig.java  
  ✓ 更新 application.yml
  ✓ 编写测试
  ✓ 生成文档
  ✓ 运行追溯检查 - 通过
  
  已完成所有修改，准备创建 PR。
  
  分支: feature/REQ-20260211-001-websocket-support
  
  请执行:
  git push origin feature/REQ-20260211-001-websocket-support
  
  然后在 GitHub 创建 PR。
  
  我会持续监控，当 PR 合并后自动关闭需求。
```

### 示例 2: 纯问题

```
用户: Streamable 协议和 SSE 有什么区别？

Agent:
  [自动分析]
  类型: pure_question
  不需要代码修改
  
  [直接回答]
  Streamable 协议和 SSE 的主要区别：
  
  1. 传输格式:
     - SSE: text/event-stream
     - Streamable: application/x-ndjson
  
  2. Session 管理:
     - SSE: 隐式（通过连接）
     - Streamable: 显式（Session ID）
  
  3. 兼容性:
     - 详见: docs/explanations/workflow-comparison.md
  
  是否需要我创建更详细的教程文档？
  [1] 是，创建教程
  [2] 不需要
```

---

## 追溯脚本调用时机

```bash
# 1. PR 创建前（自动）
// turbo
./scripts/maintenance/check-traceability.sh

# 2. 文档更新后（自动）
// turbo
python3 scripts/maintenance/check-doc-references.py

# 3. 每日定时（GitHub Actions）
# .github/workflows/daily-traceability-check.yml
```

---

## 状态追踪文件

Agent 维护一个状态文件: `.agent/state/requirements.json`

```json
{
  "REQ-20260211-001": {
    "id": "REQ-20260211-001",
    "title": "WebSocket传输支持",
    "type": "feature_request",
    "status": "in_progress",
    "created": "2026-02-11T10:00:00",
    "branch": "feature/REQ-20260211-001-websocket-support",
    "pr": null,
    "merged": false,
    "traceability": {
      "requirement": "docs/requirements/REQ-20260211-001.md",
      "design": null,
      "code": ["WebSocketHandler.java", "McpRouterServerConfig.java"],
      "test": ["WebSocketHandlerTest.java", "test_websocket.sh"],
      "documentation": ["docs/features/websocket-transport.md"]
    }
  }
}
```

**状态流转**:
```
draft → in_progress → pr_created → merged → completed
```

---

**维护者**: AI Agent  
**最后更新**: 2026-02-11  
**版本**: 1.0
