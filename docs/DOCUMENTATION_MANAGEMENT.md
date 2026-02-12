# 文档管理方案 (Documentation Management Strategy)

> 基于 Docs as Code 和文档生命周期管理的最佳实践
> 
> 创建时间: 2026-01-28

---

## 📋 目录

1. [问题陈述](#问题陈述)
2. [业界最佳实践](#业界最佳实践)
3. [我们的解决方案](#我们的解决方案)
4. [文档分类体系](#文档分类体系)
5. [文档生命周期](#文档生命周期)
6. [文档维护策略](#文档维护策略)
7. [实施计划](#实施计划)

---

## 问题陈述

### 当前痛点

正如您所提到的，随着项目发展，我们面临：

```
文档越来越多 → 难以查找和维护
脚本越来越多 → 不知道哪些还在用
信息分散     → 重复和冲突
缺乏规范     → 质量参差不齐
```

### 核心挑战

1. **发现性差**: 难以快速找到需要的文档
2. **过时文档**: 不确定哪些文档还有效
3. **重复内容**: 同样信息出现在多个地方
4. **维护困难**: 缺少明确的维护责任和周期

---

## 业界最佳实践

### 1. Diátaxis 框架 (文档分类)

来自 Divio 的 Diátaxis 是业界公认的文档分类框架：

```
┌─────────────────────────────────────────┐
│         Diátaxis 文档四象限              │
├───────────────┬─────────────────────────┤
│  学习导向      │   目标导向               │
│  TUTORIALS    │   HOW-TO GUIDES         │
│  教程         │   操作指南               │
│  (学习)       │   (目标)                 │
├───────────────┼─────────────────────────┤
│  理解导向      │   信息导向               │
│  EXPLANATION  │   REFERENCE             │
│  说明         │   参考                   │
│  (理解)       │   (信息)                 │
└───────────────┴─────────────────────────┘
```

**四种文档类型**:

| 类型 | 目的 | 读者心态 | 示例 |
|------|------|---------|------|
| **Tutorial** | 学习 | "教我" | 快速开始指南 |
| **How-To Guide** | 达成目标 | "帮我" | 添加MCP Server步骤 |
| **Explanation** | 理解 | "告诉我为什么" | 架构设计文档 |
| **Reference** | 查询信息 | "告诉我是什么" | API文档 |

### 2. Docs as Code

将文档当作代码管理：

- ✅ 版本控制 (Git)
- ✅ Pull Request 审查
- ✅ CI/CD 自动化
- ✅ 纯文本格式 (Markdown)
- ✅ 与代码放在一起

### 3. 文档生命周期管理

```
创建 → 审查 → 发布 → 维护 → 归档 → 删除
  ↓      ↓      ↓      ↓      ↓      ↓
 Draft  Review  Live  Update Archive Delete
```

### 4. 单一信息源 (Single Source of Truth)

- 每条信息只在一个地方维护
- 其他地方通过链接引用
- 避免重复和不一致

---

## 我们的解决方案

### 文档组织结构 (新)

<function_calls>
<invoke name="write_to_file">
<parameter name="CodeContent">```
mcp-router-sse-parent/
│
├── docs/                           # 📚 所有文档的主目录
│   │
│   ├── README.md                   # 📖 文档导航（单一入口）
│   │
│   ├── 01-tutorials/               # 🎓 教程（学习导向）
│   │   ├── quick-start.md         # 5分钟快速开始
│   │   ├── first-mcp-server.md    # 第一个MCP Server
│   │   └── first-agent.md         # 第一个AI Agent
│   │
│   ├── 02-how-to-guides/          # 🛠️ 操作指南（目标导向）
│   │   ├── add-mcp-server.md     # 如何添加MCP Server
│   │   ├── add-agent.md          # 如何添加Agent
│   │   ├── integrate-gemini.md   # 如何集成Gemini
│   │   └── troubleshooting.md    # 故障排除
│   │
│   ├── 03-explanations/           # 💡 说明文档（理解导向）
│   │   ├── architecture.md       # 架构说明
│   │   ├── workflow-comparison.md # 工作流对比
│   │   ├── design-decisions.md   # 设计决策
│   │   ├── bug-fixes/            # 🐛 Bug修复归档
│   │   │   ├── README.md         # Bug修复索引
│   │   │   └── REQ-*.md          # 具体Bug修复文档
│   │   └── why-spring-ai.md      # 为什么用Spring AI
│   │
│   ├── 04-reference/              # 📋 参考文档（信息导向）
│   │   ├── api/                  # API文档
│   │   ├── configuration.md      # 配置参考
│   │   ├── cli-commands.md       # 命令行参考
│   │   └── glossary.md           # 术语表
│   │
│   ├── 05-workflows/              # 🔄 工作流（移动到这里）
│   │   ├── development.md        # 开发工作流
│   │   ├── ci-cd.md             # CI/CD流程
│   │   └── release.md           # 发布流程
│   │
│   ├── 06-archived/               # 📦 已归档文档
│   │   ├── README.md            # 归档说明
│   │   └── [deprecated docs]    # 过时文档
│   │
│   └── _meta/                     # 🔧 元数据（文档的文档）
│       ├── doc-standards.md     # 文档规范
│       ├── doc-lifecycle.md     # 生命周期说明
│       ├── maintenance-log.md   # 维护日志
│       └── templates/           # 文档模板
│           ├── tutorial.md
│           ├── how-to.md
│           ├── explanation.md
│           └── reference.md
│
├── .agent/                         # 🤖 AI工作流（保持不变）
│   └── workflows/
│       ├── add-mcp-server.md
│       ├── add-agent-workflow.md
│       └── review.md
│
├── scripts/                        # 🔧 脚本（统一管理）
│   ├── README.md                  # 脚本说明
│   ├── dev/                       # 开发脚本
│   │   ├── setup.sh
│   │   └── demo.sh
│   ├── build/                     # 构建脚本
│   ├── deploy/                    # 部署脚本
│   └── maintenance/               # 维护脚本
│       ├── update-docs.sh
│       └── check-links.sh
│
└── [module directories]
```

### 关键改进

1. **按 Diátaxis 分类**: 清晰的文档分类
2. **单一入口**: `docs/README.md` 作为导航中心
3. **脚本统一管理**: 所有脚本放在 `scripts/` 目录
4. **归档机制**: 保留但标记过时文档
5. **元数据**: 文档的文档，维护规范和日志

---

## 文档分类体系

### 迁移计划

#### 现有文档 → 新位置

| 现有文档 | 类型 | 新位置 | 备注 |
|---------|------|--------|------|
| `docs/QUICK_START.md` | Tutorial | `docs/01-tutorials/quick-start.md` | 重命名 |
| `docs/GEMINI_INTEGRATION_GUIDE.md` | How-To | `docs/02-how-to-guides/integrate-gemini.md` | 移动 |
| `docs/GITHUB_WORKFLOWS_COMPARISON.md` | Explanation | `docs/03-explanations/workflow-comparison.md` | 移动 |
| `docs/GOOGLE_DEEPMIND_INTEGRATION_PLAN.md` | Explanation | `docs/03-explanations/gemini-plan.md` | 移动 |
| `docs/START_HERE.md` | 特殊 | `docs/README.md` | 合并为导航 |
| `.agent/workflows/*.md` | Reference | 保持不变 | AI工作流 |
| `demo.sh` | Tool | `scripts/dev/demo.sh` | 移动 |

#### 新创建的文档位置

| 文档 | 类型 | 位置 |
|------|------|------|
| 架构文档 | Explanation | `docs/03-explanations/architecture.md` |
| API参考 | Reference | `docs/04-reference/api/` |
| 配置指南 | Reference | `docs/04-reference/configuration.md` |
| 故障排除 | How-To | `docs/02-how-to-guides/troubleshooting.md` |

---

## 文档生命周期

### 状态标记系统

每个文档顶部添加 YAML frontmatter：

```markdown
---
status: active          # active | draft | review | deprecated | archived
created: 2026-01-28
last_updated: 2026-01-28
review_date: 2026-04-28  # 每3个月审查一次
owner: team-name
tags: [tutorial, mcp-server, getting-started]
---
```

### 生命周期阶段

```
1. Draft (草稿)
   ├─ 作者编写
   └─ status: draft

2. Review (审查)
   ├─ 团队审查
   ├─ 修改完善
   └─ status: review

3. Active (活跃)
   ├─ 发布使用
   ├─ 定期更新
   └─ status: active

4. Deprecated (弃用)
   ├─ 标记为过时
   ├─ 指向新文档
   └─ status: deprecated

5. Archived (归档)
   ├─ 移至归档目录
   └─ status: archived

6. Deleted (删除)
   └─ 彻底删除
```

### 自动化检查

创建 `.github/workflows/docs-check.yml`:

```yaml
name: Documentation Check

on:
  schedule:
    - cron: '0 0 1 * *'  # 每月1号检查
  workflow_dispatch:

jobs:
  check-docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Check for outdated docs
        run: |
          # 检查review_date过期的文档
          python scripts/maintenance/check-doc-freshness.py
      
      - name: Check broken links
        run: |
          # 检查损坏的链接
          npm install -g markdown-link-check
          find docs -name '*.md' -exec markdown-link-check {} \;
      
      - name: Create issue if found
        if: failure()
        run: |
          # 创建Issue提醒更新
```

---

## 文档维护策略

### 1. 维护责任

| 文档类型 | 维护者 | 审查周期 |
|---------|-------|---------|
| Tutorials | 技术写作团队 | 每3个月 |
| How-To Guides | 功能负责人 | 代码变更时 |
| Explanations | 架构师 | 每6个月 |
| Reference | 自动生成 + 开发者 | 每次发版 |
| Workflows | DevOps团队 | 流程变更时 |

### 2. 更新触发条件

**必须更新文档的情况**:

- ✅ 代码功能变更
- ✅ API接口变更
- ✅ 配置项变更
- ✅ 工作流程变更
- ✅ Bug修复影响使用

**审查触发**:

- 🔍 定期审查 (review_date)
- 🔍 用户反馈
- 🔍 Bug报告中提到文档问题

### 3. 质量标准

#### Checklist

每个文档必须：

- [ ] 有清晰的标题和描述
- [ ] 有 frontmatter 元数据
- [ ] 有目标受众说明
- [ ] 有实际示例
- [ ] 链接都有效
- [ ] 代码示例可运行
- [ ] 语法和拼写正确
- [ ] 遵循统一的风格

#### 使用模板

所有新文档从模板开始:

```bash
# 创建教程
cp docs/_meta/templates/tutorial.md docs/01-tutorials/my-tutorial.md

# 创建操作指南
cp docs/_meta/templates/how-to.md docs/02-how-to-guides/my-guide.md
```

---

## 实施计划

### 阶段 1: 立即执行（今天）

1. **创建新目录结构**
   ```bash
   mkdir -p docs/{01-tutorials,02-how-to-guides,03-explanations,04-reference,05-workflows,06-archived,_meta/templates}
   mkdir -p scripts/{dev,build,deploy,maintenance}
   ```

2. **创建文档导航** (`docs/README.md`)
3. **创建文档模板** (`docs/_meta/templates/`)
4. **创建维护脚本**

### 阶段 2: 本周执行

1. **迁移现有文档**
   - 按照分类移动文档
   - 添加 frontmatter
   - 更新所有链接

2. **创建文档标准** (`docs/_meta/doc-standards.md`)
3. **设置自动化检查**

### 阶段 3: 本月执行

1. **补充缺失文档**
   - API参考
   - 配置指南
   - 故障排除

2. **团队培训**
   - 文档规范
   - 维护流程

3. **建立审查机制**

---

## 工具和自动化

### 推荐工具

1. **文档生成**: 
   - MkDocs / Docusaurus (静态站点)
   - Swagger / OpenAPI (API文档)

2. **链接检查**:
   ```bash
   npm install -g markdown-link-check
   ```

3. **拼写检查**:
   ```bash
   npm install -g markdown-spellcheck
   ```

4. **文档Lint**:
   ```bash
   npm install -g markdownlint-cli
   ```

### 自动化脚本

创建 `scripts/maintenance/update-docs.sh`:

```bash
#!/bin/bash
# 更新文档元数据
find docs -name '*.md' -exec sed -i '' "s/last_updated:.*/last_updated: $(date +%Y-%m-%d)/" {} \;
```

---

## 成功指标

### 可测量的目标

| 指标 | 目标 | 当前 |
|------|------|------|
| 文档覆盖率 | 100% 核心功能 | - |
| 过时文档 | < 5% | - |
| 平均查找时间 | < 2分钟 | - |
| 用户满意度 | > 4.0/5.0 | - |
| 损坏链接 | 0 | - |

### 定期报告

每月生成报告:

```bash
# 文档健康度报告
scripts/maintenance/doc-health-report.sh

输出:
- 总文档数
- 待审查文档数
- 过期文档数
- 损坏链接数
- 本月新增/更新文档数
```

---

## 常见问题

### Q: 如何决定文档应该放在哪个分类？

**A**: 问自己读者的目的：
- 要**学习**基础 → Tutorial
- 要**完成**任务 → How-To
- 要**理解**概念 → Explanation
- 要**查询**信息 → Reference

### Q: 旧文档怎么处理？

**A**: 
1. 评估是否还需要
2. 需要 → 更新并移到新位置
3. 不需要但有价值 → 归档
4. 完全过时 → 删除（但保留在Git历史）

### Q: 如何避免文档过时？

**A**:
1. 设置 `review_date`
2. 代码变更时同步更新文档
3. 自动化检查
4. 用户反馈渠道

### Q: 文档太多，找不到怎么办？

**A**:
1. 使用 `docs/README.md` 导航
2. 按照 Diátaxis 分类查找
3. 搜索功能（如果有文档站点）
4. 标签系统

---

## 参考资源

- [Diátaxis Documentation Framework](https://diataxis.fr/)
- [Docs as Code](https://www.writethedocs.org/guide/docs-as-code/)
- [Google Developer Documentation Style Guide](https://developers.google.com/style)
- [Microsoft Writing Style Guide](https://learn.microsoft.com/en-us/style-guide/welcome/)

---

**文档版本**: 1.0  
**创建时间**: 2026-01-28  
**下次审查**: 2026-04-28  
**负责人**: Documentation Team
