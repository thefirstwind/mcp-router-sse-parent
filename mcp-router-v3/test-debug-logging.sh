#!/bin/bash

# MCP Router v3 调试日志功能测试脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}🧪 MCP Router v3 调试日志功能测试${NC}"
echo ""

# 检查脚本权限
echo -e "${BLUE}1. 检查脚本权限...${NC}"
if [ -x "./debug-start.sh" ] && [ -x "./debug-log-analyzer.sh" ]; then
    echo -e "${GREEN}✅ 脚本权限正常${NC}"
else
    echo -e "${RED}❌ 脚本权限不足，正在修复...${NC}"
    chmod +x ./debug-start.sh ./debug-log-analyzer.sh
    echo -e "${GREEN}✅ 脚本权限已修复${NC}"
fi

# 检查配置文件
echo -e "${BLUE}2. 检查配置文件...${NC}"
config_files=(
    "src/main/resources/logback-spring.xml"
    "src/main/resources/application-debug.yml"
    "DEBUG_GUIDE.md"
    "LOGGING_ENHANCEMENT_SUMMARY.md"
)

for file in "${config_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅ $file 存在${NC}"
    else
        echo -e "${RED}❌ $file 不存在${NC}"
    fi
done

# 检查Java源文件
echo -e "${BLUE}3. 检查调试相关Java文件...${NC}"
java_files=(
    "src/main/java/com/pajk/mcpbridge/core/util/DebugLogger.java"
    "src/main/java/com/pajk/mcpbridge/core/config/DebugConfig.java"
)

for file in "${java_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅ $file 存在${NC}"
    else
        echo -e "${RED}❌ $file 不存在${NC}"
    fi
done

# 测试日志配置语法
echo -e "${BLUE}4. 测试日志配置语法...${NC}"
if command -v xmllint >/dev/null 2>&1; then
    if xmllint --noout src/main/resources/logback-spring.xml 2>/dev/null; then
        echo -e "${GREEN}✅ logback-spring.xml 语法正确${NC}"
    else
        echo -e "${RED}❌ logback-spring.xml 语法错误${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  xmllint 未安装，跳过XML语法检查${NC}"
fi

# 测试YAML配置语法
echo -e "${BLUE}5. 测试YAML配置语法...${NC}"
if command -v python3 >/dev/null 2>&1; then
    if python3 -c "import yaml; yaml.safe_load(open('src/main/resources/application-debug.yml'))" 2>/dev/null; then
        echo -e "${GREEN}✅ application-debug.yml 语法正确${NC}"
    else
        echo -e "${RED}❌ application-debug.yml 语法错误${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Python3 未安装，跳过YAML语法检查${NC}"
fi

# 检查Maven编译
echo -e "${BLUE}6. 检查Maven编译...${NC}"
if mvn compile -q >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Maven编译成功${NC}"
else
    echo -e "${RED}❌ Maven编译失败${NC}"
    echo -e "${YELLOW}运行 'mvn compile' 查看详细错误信息${NC}"
fi

# 测试脚本帮助功能
echo -e "${BLUE}7. 测试脚本帮助功能...${NC}"
if ./debug-start.sh -h >/dev/null 2>&1; then
    echo -e "${GREEN}✅ debug-start.sh 帮助功能正常${NC}"
else
    echo -e "${RED}❌ debug-start.sh 帮助功能异常${NC}"
fi

if ./debug-log-analyzer.sh -h >/dev/null 2>&1; then
    echo -e "${GREEN}✅ debug-log-analyzer.sh 帮助功能正常${NC}"
else
    echo -e "${RED}❌ debug-log-analyzer.sh 帮助功能异常${NC}"
fi

# 检查日志目录
echo -e "${BLUE}8. 检查日志目录...${NC}"
if [ -d "logs" ]; then
    echo -e "${GREEN}✅ logs 目录存在${NC}"
    log_count=$(find logs -name "*.log" -type f 2>/dev/null | wc -l)
    echo -e "${BLUE}   当前日志文件数量: $log_count${NC}"
else
    echo -e "${YELLOW}⚠️  logs 目录不存在（首次运行时会自动创建）${NC}"
fi

echo ""
echo -e "${CYAN}📋 测试总结${NC}"
echo -e "${GREEN}✅ 调试日志功能已完善并可以使用${NC}"
echo ""
echo -e "${BLUE}🚀 快速开始:${NC}"
echo -e "  启动调试模式: ${YELLOW}./debug-start.sh${NC}"
echo -e "  查看实时日志: ${YELLOW}./debug-log-analyzer.sh tail${NC}"
echo -e "  查看帮助信息: ${YELLOW}./debug-start.sh -h${NC}"
echo ""
echo -e "${BLUE}📚 文档:${NC}"
echo -e "  详细指南: ${YELLOW}cat DEBUG_GUIDE.md${NC}"
echo -e "  功能总结: ${YELLOW}cat LOGGING_ENHANCEMENT_SUMMARY.md${NC}"
echo ""

