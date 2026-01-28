# 📚 文档管理快速参考

> 基于 Diátaxis 框架的文档管理速查表

---

## 🎯 我要...

### 创建新文档

```bash
# 1. 确定文档类型
我要帮助用户【学习】    → Tutorial     (教程)
我要帮助用户【完成任务】 → How-To Guide (操作指南)
我要帮助用户【理解】    → Explanation (说明)
我要帮助用户【查询信息】 → Reference    (参考)

# 2. 使用模板
cp docs/_meta/templates/[类型].md docs/[目录]/my-doc.md

# 3. 填写frontmatter
---
status: draft
created: 2026-01-28
last_updated: 2026-01-28
review_date: 2026-04-28
owner: your-name
tags: [tag1, tag2]
---

# 4. 编写内容
# 5. 提交PR
```

### 查找文档

```
docs/
├── 01-tutorials/        # 我要学习
├── 02-how-to-guides/    # 我要完成任务
├── 03-explanations/     # 我要理解原理
├── 04-reference/        # 我要查询API
├── 05-workflows/        # 我要了解流程
└── README.md            # 📍 从这里开始
```

### 更新现有文档

```bash
# 1. 修改文档
# 2. 更新 last_updated
last_updated: $(date +%Y-%m-%d)

# 3. 如果重大变更，更新 review_date
review_date: $(date -v+3m +%Y-%m-%d)  # Mac
review_date: $(date -d "+3 months" +%Y-%m-%d)  # Linux

# 4. 提交PR
```

### 归档过时文档

```bash
# 1. 标记为deprecated
status: deprecated

# 2. 添加弃用说明
> ⚠️ **已弃用**: 此文档已过时，请参考 [新文档](link)

# 3. 稍后移至归档
mv docs/XX/old-doc.md docs/06-archived/
```

---

## 📋 Diátaxis 快速决策

### 问自己这些问题

```
读者想要什么？
├─ 学习基础知识
│  └─ Tutorial
├─ 完成具体任务
│  └─ How-To Guide
├─ 理解为什么/如何工作
│  └─ Explanation
└─ 查找特定信息
   └─ Reference
```

### 示例

| 标题 | 类型 | 理由 |
|------|------|------|
| "构建你的第一个MCP Server" | Tutorial | 教学习者一步步完成 |
| "如何添加Gemini集成" | How-To | 完成特定任务 |
| "为什么选择Spring AI" | Explanation | 理解决策原因 |
| "API参考" | Reference | 查询具体信息 |

---

## ✅ 文档质量检查清单

### 基本要求
- [ ] 有清晰的标题
- [ ] 有frontmatter元数据
- [ ] 状态正确 (draft/active/deprecated)
- [ ] 日期准确
- [ ] 有合适的标签

### Tutorial特定
- [ ] 有明确的学习目标
- [ ] 步骤清晰，可执行
- [ ] 有验证方法
- [ ] 有"下一步"指引

### How-To特定
- [ ] 解决具体问题
- [ ] 有前置条件
- [ ] 步骤简洁明了
- [ ] 有故障排除

### Explanation特定
- [ ] 解释"为什么"
- [ ] 有背景信息
- [ ] 有图表/示例
- [ ] 链接到相关资源

### Reference特定
- [ ] 信息准确完整
- [ ] 组织清晰
- [ ] 易于扫描查找
- [ ] 有代码示例

---

## 🔄 文档生命周期

```
Draft → Review → Active → (Update) → Deprecated → Archived
  ↓       ↓        ↓          ↓          ↓           ↓
草稿    审查      发布       维护       弃用        归档
```

### 状态转换

```bash
# Draft → Review
status: review

# Review → Active (审查通过)
status: active

# Active → Deprecated (有新版本)
status: deprecated
在顶部添加: > ⚠️ **已弃用**: 请使用 [新文档](link)

# Deprecated → Archived after 6个月
mv docs/XX/doc.md docs/06-archived/
```

---

## 🛠️ 常用命令

### 检查文档健康度

```bash
# 查找需要审查的文档
find docs -name '*.md' -exec grep -l "review_date: $(date +%Y-%m)" {} \;

# 查找损坏的链接
markdown-link-check docs/**/*.md

# 拼写检查
markdown-spellcheck docs/**/*.md

# Lint检查
markdownlint docs/**/*.md
```

### 批量更新

```bash
# 更新所有文档的last_updated
find docs -name '*.md' -exec sed -i '' "s/last_updated:.*/last_updated: $(date +%Y-%m-%d)/" {} \;

# 查找没有frontmatter的文档
find docs -name '*.md' ! -exec grep -q "^---$" {} \; -print
```

---

## 📊 维护时间表

| 频率 | 任务 | 负责人 |
|------|------|--------|
| **每周** | 审查新PR中的文档 | PR审查者 |
| **每月** | 检查损坏的链接 | 自动化 |
| **每季度** | 审查需要更新的文档 | 文档负责人 |
| **每半年** | 归档过时文档 | 团队 |
| **每年** | 全面审查文档结构 | 架构师 |

---

## 💡 最佳实践

### DO ✅

- ✅ 使用模板创建新文档
- ✅ 保持frontmatter更新
- ✅ 链接到权威信息源
- ✅ 添加实际可运行的示例
- ✅ 使用清晰的章节标题
- ✅ 定期审查和更新

### DON'T ❌

- ❌ 复制粘贴信息（使用链接）
- ❌ 使用模糊的标题
- ❌ 省略前置条件
- ❌ 混合多种文档类型
- ❌ 忘记更新链接
- ❌ 让文档长期处于draft状态

---

## 🆘 遇到问题？

### 不确定文档类型？

查看 [DOCUMENTATION_MANAGEMENT.md](./DOCUMENTATION_MANAGEMENT.md#文档分类体系)

### 不知道放在哪里？

查看 [新目录结构](./DOCUMENTATION_MANAGEMENT.md#我们的解决方案)

### 链接怎么写？

```markdown
# 相对路径（推荐）
[文本](../02-how-to-guides/guide.md)

# 绝对路径（如果需要）
[文本](/docs/02-how-to-guides/guide.md)

# 外部链接
[文本](https://example.com)
```

### 模板在哪里？

```
docs/_meta/templates/
├── tutorial.md
├── how-to.md
├── explanation.md
└── reference.md
```

---

## 🔗 相关资源

- [完整文档管理方案](./DOCUMENTATION_MANAGEMENT.md)
- [Diátaxis Framework](https://diataxis.fr/)
- [Markdown Guide](https://www.markdownguide.org/)
- [Git Workflow](../../CONTRIBUTING.md)

---

**快速访问**: 将此文件加入书签 🔖  
**需要帮助**: [创建Issue](../../issues/new)
