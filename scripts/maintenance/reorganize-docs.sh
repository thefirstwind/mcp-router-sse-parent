#!/bin/bash

# 文档重组脚本
# 用途：按照新的文档管理方案重新组织文档

set -e

echo "========================================="
echo "  文档重组脚本"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 备份现有文档
backup_docs() {
    echo -e "${BLUE}1. 备份现有文档...${NC}"
    
    BACKUP_DIR="docs/backup-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    # 复制所有文档到备份目录
    find docs -maxdepth 1 -name '*.md' -exec cp {} "$BACKUP_DIR/" \;
    
    echo -e "${GREEN}✓ 备份完成: $BACKUP_DIR${NC}"
    echo ""
}

# 迁移文档到新位置
migrate_docs() {
    echo -e "${BLUE}2. 迁移文档到新位置...${NC}"
    
    # Tutorials
    if [ -f "docs/QUICK_START.md" ]; then
        mv docs/QUICK_START.md docs/01-tutorials/quick-start.md
        echo -e "${GREEN}✓ QUICK_START.md → 01-tutorials/quick-start.md${NC}"
    fi
    
    # How-To Guides
    if [ -f "docs/GEMINI_INTEGRATION_GUIDE.md" ]; then
        mv docs/GEMINI_INTEGRATION_GUIDE.md docs/02-how-to-guides/integrate-gemini.md
        echo -e "${GREEN}✓ GEMINI_INTEGRATION_GUIDE.md → 02-how-to-guides/integrate-gemini.md${NC}"
    fi
    
    # Explanations
    if [ -f "docs/GITHUB_WORKFLOWS_COMPARISON.md" ]; then
        mv docs/GITHUB_WORKFLOWS_COMPARISON.md docs/03-explanations/workflow-comparison.md
        echo -e "${GREEN}✓ GITHUB_WORKFLOWS_COMPARISON.md → 03-explanations/workflow-comparison.md${NC}"
    fi
    
    if [ -f "docs/GOOGLE_DEEPMIND_INTEGRATION_PLAN.md" ]; then
        mv docs/GOOGLE_DEEPMIND_INTEGRATION_PLAN.md docs/03-explanations/gemini-plan.md
        echo -e "${GREEN}✓ GOOGLE_DEEPMIND_INTEGRATION_PLAN.md → 03-explanations/gemini-plan.md${NC}"
    fi
    
    # Workflows
    if [ -f "docs/WORKFLOWS_SUMMARY.md" ]; then
        mv docs/WORKFLOWS_SUMMARY.md docs/05-workflows/summary.md
        echo -e "${GREEN}✓ WORKFLOWS_SUMMARY.md → 05-workflows/summary.md${NC}"
    fi
    
    echo ""
}

# 移动脚本
migrate_scripts() {
    echo -e "${BLUE}3. 移动脚本...${NC}"
    
    if [ -f "demo.sh" ]; then
        mv demo.sh scripts/dev/demo.sh
        chmod +x scripts/dev/demo.sh
        echo -e "${GREEN}✓ demo.sh → scripts/dev/demo.sh${NC}"
    fi
    
    echo ""
}

# 归档过时文档
archive_old_docs() {
    echo -e "${BLUE}4. 归档过时文档...${NC}"
    
    # 将TODO目录移到归档
    if [ -d "docs/TODO" ]; then
        mv docs/TODO docs/06-archived/
        echo -e "${GREEN}✓ TODO目录 → 06-archived/TODO${NC}"
    fi
    
    # 其他可能过时的文档
    for dir in docs/docs-*; do
        if [ -d "$dir" ]; then
            mv "$dir" docs/06-archived/
            echo -e "${GREEN}✓ $(basename $dir) → 06-archived/$(basename $dir)${NC}"
        fi
    done
    
    echo ""
}

# 创建新的文档导航
create_navigation() {
    echo -e "${BLUE}5. 创建文档导航...${NC}"
    
    # 这将由另一个脚本创建
    echo -e "${YELLOW}⚠ 文档导航将由单独的脚本创建${NC}"
    echo ""
}

# 更新链接
update_links() {
    echo -e "${BLUE}6. 更新文档链接...${NC}"
    echo -e "${YELLOW}⚠ 警告：这是一个复杂的操作，建议手动检查${NC}"
    
    read -p "是否尝试自动更新链接？(y/n) " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        # 示例：更新README中的链接
        if [ -f "README.md" ]; then
            # 备份
            cp README.md README.md.backup
            
            # 替换链接（示例）
            sed -i.bak 's|docs/QUICK_START.md|docs/01-tutorials/quick-start.md|g' README.md
            
            echo -e "${GREEN}✓ 已尝试更新README链接${NC}"
            echo -e "${YELLOW}  请手动检查并确认${NC}"
        fi
    else
        echo -e "${YELLOW}  跳过自动更新链接${NC}"
    fi
    
    echo ""
}

# 生成报告
generate_report() {
    echo -e "${BLUE}7. 生成迁移报告...${NC}"
    
    REPORT_FILE="docs/_meta/migration-report-$(date +%Y%m%d).md"
    
    cat > "$REPORT_FILE" << EOF
# 文档迁移报告

**日期**: $(date +%Y-%m-%d)

## 迁移的文档

### Tutorials
- QUICK_START.md → 01-tutorials/quick-start.md

### How-To Guides
- GEMINI_INTEGRATION_GUIDE.md → 02-how-to-guides/integrate-gemini.md

### Explanations
- GITHUB_WORKFLOWS_COMPARISON.md → 03-explanations/workflow-comparison.md
- GOOGLE_DEEPMIND_INTEGRATION_PLAN.md → 03-explanations/gemini-plan.md

### Workflows
- WORKFLOWS_SUMMARY.md → 05-workflows/summary.md

## 归档的文档

- docs/TODO → docs/06-archived/TODO
- docs/docs-* → docs/06-archived/

## 移动的脚本

- demo.sh → scripts/dev/demo.sh

## 下一步

1. [ ] 检查所有链接是否有效
2. [ ] 为每个文档添加frontmatter
3. [ ] 创建新的docs/README.md导航
4. [ ] 补充缺失的文档
5. [ ] 团队培训

## 备份位置

$(ls -d docs/backup-*)
EOF

    echo -e "${GREEN}✓ 报告已生成: $REPORT_FILE${NC}"
    echo ""
}

# 主函数
main() {
    echo -e "${YELLOW}这将重新组织您的文档结构${NC}"
    echo -e "${YELLOW}请确保您已经阅读了 docs/DOCUMENTATION_MANAGEMENT.md${NC}"
    echo ""
    
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}已取消${NC}"
        exit 1
    fi
    
    backup_docs
    migrate_docs
    migrate_scripts
    archive_old_docs
    create_navigation
    update_links
    generate_report
    
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}  迁移完成！${NC}"
    echo -e "${GREEN}=========================================${NC}"
    echo ""
    echo -e "${BLUE}下一步：${NC}"
    echo "  1. 检查迁移报告"
    echo "  2. 手动验证链接"
    echo "  3. 创建docs/README.md导航"
    echo "  4. 为文档添加frontmatter"
    echo ""
    echo -e "${YELLOW}如果遇到问题，可以从备份恢复${NC}"
}

# 运行主函数
main
