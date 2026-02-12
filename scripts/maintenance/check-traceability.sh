#!/bin/bash
# 追溯完整性检查脚本
# 检查每个需求是否有完整的追溯链：需求 → 设计 → 代码 → 测试 → 文档

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 统计变量
TOTAL_REQUIREMENTS=0
COMPLETE_REQUIREMENTS=0
INCOMPLETE_REQUIREMENTS=0

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}追溯完整性检查${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# 1. 提取所有需求ID
echo -e "${YELLOW}步骤 1: 提取所有需求ID...${NC}"
REQUIREMENTS=$(find docs/ .github/ -type f -name "*.md" -exec grep -h "REQ-[0-9][0-9-]*" {} \; 2>/dev/null | \
    grep -oE 'REQ-[0-9]{8}-[0-9]+|REQ-[0-9]+' | sort -u)

if [ -z "$REQUIREMENTS" ]; then
    echo -e "${YELLOW}未找到任何需求ID (REQ-XXX)${NC}"
    echo "提示：在文档中使用 REQ-001, REQ-002 等格式标记需求"
    exit 0
fi

echo -e "${GREEN}找到以下需求：${NC}"
echo "$REQUIREMENTS" | sed 's/^/  - /'
TOTAL_REQUIREMENTS=$(echo "$REQUIREMENTS" | wc -l)
echo ""

# 2. 检查每个需求的追溯链
for req in $REQUIREMENTS; do
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}检查 $req${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    MISSING_ITEMS=()
    
    # 2.1 检查设计文档（ADR或架构文档）
    echo -n "  设计文档: "
    if grep -r "$req" docs/adr/ docs/explanations/ 2>/dev/null | grep -q "$req"; then
        echo -e "${GREEN}✓ 找到${NC}"
        DESIGN_FILES=$(grep -rl "$req" docs/adr/ docs/explanations/ 2>/dev/null | head -3)
        echo "$DESIGN_FILES" | sed 's/^/    - /'
    else
        echo -e "${RED}✗ 缺失${NC}"
        MISSING_ITEMS+=("设计文档")
    fi
    
    # 2.2 检查代码实现（Java文件中的@traceability标记）
    echo -n "  代码实现: "
    if find . -name "*.java" -exec grep -l "@traceability.*$req" {} \; 2>/dev/null | grep -q .; then
        echo -e "${GREEN}✓ 找到${NC}"
        CODE_FILES=$(find . -name "*.java" -exec grep -l "@traceability.*$req" {} \; 2>/dev/null | head -3)
        echo "$CODE_FILES" | sed 's/^/    - /'
    else
        echo -e "${RED}✗ 缺失${NC}"
        MISSING_ITEMS+=("代码实现")
    fi
    
    # 2.3 检查测试（测试文件和脚本）
    echo -n "  测试验证: "
    TEST_FOUND=false
    
    # 检查测试脚本
    if find testScript/ scripts/ -type f -name "*.sh" -exec grep -l "$req" {} \; 2>/dev/null | grep -q .; then
        TEST_FOUND=true
    fi
    
    # 检查Java测试文件
    if find . -name "*Test.java" -exec grep -l "$req" {} \; 2>/dev/null | grep -q .; then
        TEST_FOUND=true
    fi
    
    # 检查GitHub Actions
    if grep -r "$req" .github/workflows/ 2>/dev/null | grep -q "$req"; then
        TEST_FOUND=true
    fi
    
    if [ "$TEST_FOUND" = true ]; then
        echo -e "${GREEN}✓ 找到${NC}"
        # 显示测试文件
        find testScript/ scripts/ -type f -name "*.sh" -exec grep -l "$req" {} \; 2>/dev/null | head -2 | sed 's/^/    - /'
        find . -name "*Test.java" -exec grep -l "$req" {} \; 2>/dev/null | head -2 | sed 's/^/    - /'
    else
        echo -e "${RED}✗ 缺失${NC}"
        MISSING_ITEMS+=("测试验证")
    fi
    
    # 2.4 检查用户文档
    echo -n "  用户文档: "
    if grep -r "$req" docs/tutorials/ docs/how-to-guides/ docs/features/ docs/reference/ 2>/dev/null | grep -q "$req"; then
        echo -e "${GREEN}✓ 找到${NC}"
        DOC_FILES=$(grep -rl "$req" docs/tutorials/ docs/how-to-guides/ docs/features/ docs/reference/ 2>/dev/null | head -3)
        echo "$DOC_FILES" | sed 's/^/    - /'
    else
        echo -e "${RED}✗ 缺失${NC}"
        MISSING_ITEMS+=("用户文档")
    fi
    
    # 2.5 总结
    if [ ${#MISSING_ITEMS[@]} -eq 0 ]; then
        echo -e "${GREEN}✓ $req 追溯链完整${NC}"
        ((COMPLETE_REQUIREMENTS++))
    else
        echo -e "${RED}✗ $req 追溯链不完整，缺少：${NC}"
        for item in "${MISSING_ITEMS[@]}"; do
            echo -e "${RED}    - $item${NC}"
        done
        ((INCOMPLETE_REQUIREMENTS++))
    fi
    
    echo ""
done

# 3. 生成总结报告
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}追溯检查总结${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "总需求数量: $TOTAL_REQUIREMENTS"
echo -e "${GREEN}完整追溯: $COMPLETE_REQUIREMENTS${NC}"
echo -e "${RED}不完整追溯: $INCOMPLETE_REQUIREMENTS${NC}"

COMPLETION_RATE=0
if [ $TOTAL_REQUIREMENTS -gt 0 ]; then
    COMPLETION_RATE=$((COMPLETE_REQUIREMENTS * 100 / TOTAL_REQUIREMENTS))
fi

echo ""
echo "完整率: $COMPLETION_RATE%"
echo ""

# 4. 退出码
if [ $INCOMPLETE_REQUIREMENTS -gt 0 ]; then
    echo -e "${YELLOW}建议：完善不完整的追溯链${NC}"
    exit 1
else
    echo -e "${GREEN}所有需求的追溯链都是完整的！${NC}"
    exit 0
fi
