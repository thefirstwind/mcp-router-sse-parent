#!/bin/bash

# 自动化文档重组脚本
# 这个脚本会自动将现有文档移动到MkDocs的结构中

set -e

echo "🚀 开始自动化文档重组..."
echo ""

# 创建MkDocs文档目录结构
mkdir -p docs/{quick-start,tutorials,how-to-guides,explanations,reference,workflows,contributing}

# 移动现有文档到新位置
echo "📦 移动文档到新位置..."

# Quick Start
if [ -f "docs/QUICK_START.md" ]; then
    mv docs/QUICK_START.md docs/quick-start/quick-start.md
    echo "✓ QUICK_START.md → quick-start/"
fi

if [ -f "docs/START_HERE.md" ]; then
    mv docs/START_HERE.md docs/index.md
    echo "✓ START_HERE.md → index.md"
fi

# How-To Guides
if [ -f "docs/GEMINI_INTEGRATION_GUIDE.md" ]; then
    mv docs/GEMINI_INTEGRATION_GUIDE.md docs/how-to-guides/integrate-gemini.md
    echo "✓ GEMINI_INTEGRATION_GUIDE.md → how-to-guides/"
fi

# Explanations
if [ -f "docs/GITHUB_WORKFLOWS_COMPARISON.md" ]; then
    mv docs/GITHUB_WORKFLOWS_COMPARISON.md docs/explanations/workflow-comparison.md
    echo "✓ GITHUB_WORKFLOWS_COMPARISON.md → explanations/"
fi

if [ -f "docs/GOOGLE_DEEPMIND_INTEGRATION_PLAN.md" ]; then
    mv docs/GOOGLE_DEEPMIND_INTEGRATION_PLAN.md docs/explanations/gemini-plan.md
    echo "✓ GOOGLE_DEEPMIND_INTEGRATION_PLAN.md → explanations/"
fi

# Workflows
if [ -f "docs/WORKFLOWS_SUMMARY.md" ]; then
    mv docs/WORKFLOWS_SUMMARY.md docs/workflows/summary.md
    echo "✓ WORKFLOWS_SUMMARY.md → workflows/"
fi

if [ -f "docs/GITHUB_SETUP_COMPLETE.md" ]; then
    mv docs/GITHUB_SETUP_COMPLETE.md docs/workflows/github-setup.md
    echo "✓ GITHUB_SETUP_COMPLETE.md → workflows/"
fi

# Contributing
if [ -f "CONTRIBUTING.md" ]; then
    cp CONTRIBUTING.md docs/contributing/index.md
    echo "✓ CONTRIBUTING.md → contributing/"
fi

# 创建索引文件 
echo ""
echo "📝 创建索引文件..."

# tutorials/index.md
cat > docs/tutorials/index.md << 'EOF'
# 教程

欢迎来到教程部分！这里提供循序渐进的学习指南。

## 可用教程

- [5分钟快速开始](../quick-start/quick-start.md)
- [开发环境设置](../quick-start/setup.md)

EOF

# how-to-guides/index.md
cat > docs/how-to-guides/index.md << 'EOF'
# 操作指南

这里提供完成具体任务的步骤指南。

## 可用指南

- [添加 MCP Server](add-mcp-server.md)
- [添加 AI Agent](add-agent.md)
- [集成 Gemini](integrate-gemini.md)

EOF

# explanations/index.md
cat > docs/explanations/index.md << 'EOF'
# 说明文档

深入理解项目的架构、设计决策和核心概念。

## 可用文档

- [架构设计](architecture.md)
- [工作流对比](workflow-comparison.md)
- [Gemini 整合计划](gemini-plan.md)

EOF

# reference/index.md
cat > docs/reference/index.md << 'EOF'
# 参考文档

查询具体的API、配置和命令信息。

## 可用参考

- [API 参考](api.md)
- [配置参考](configuration.md)

EOF

# workflows/index.md
cat > docs/workflows/index.md << 'EOF'
# 工作流

了解项目的开发和部署流程。

## 可用文档

- [开发工作流](development.md)
- [CI/CD 流程](ci-cd.md)
- [GitHub 设置](github-setup.md)

EOF

echo "✓ 所有索引文件已创建"

# 归档旧文档
echo ""
echo "📦 归档过时文档..."

mkdir -p docs/archived

# 移动TODO和旧文档到归档
if [ -d "docs/TODO" ]; then
    mv docs/TODO docs/archived/
    echo "✓ TODO → archived/"
fi

for dir in docs/docs-*; do
    if [ -d "$dir" ]; then
        mv "$dir" docs/archived/
        echo "✓ $(basename $dir) → archived/"
    fi
done

# 移动脚本
echo ""
echo "🔧 整理脚本..."

if [ -f "demo.sh" ]; then
    mv demo.sh scripts/dev/
    echo "✓ demo.sh → scripts/dev/"
fi

echo ""
echo "✅ 文档重组完成！"
echo ""
echo "📚 下一步："
echo "  1. git add ."
echo "  2. git commit -m 'docs: reorganize documentation for MkDocs'"
echo "  3. git push"
echo "  4. GitHub Actions 会自动构建并部署文档"
echo ""
echo "🌐 文档将发布到: https://yourname.github.io/mcp-router-sse-parent"
