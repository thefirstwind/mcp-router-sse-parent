#!/bin/bash

# MCP Router 项目演示脚本
# 用途：快速展示项目的各个组件

set -e  # 遇到错误时退出

echo "========================================="
echo "  MCP Router 项目演示"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Java 和 Maven
check_requirements() {
    echo -e "${BLUE}1. 检查环境...${NC}"
    
    if ! command -v java &> /dev/null; then
        echo -e "${YELLOW}Java 未安装或不在 PATH 中${NC}"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        echo -e "${YELLOW}Maven 未安装或不在 PATH 中${NC}"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt "17" ]; then
        echo -e "${YELLOW}需要 Java 17+，当前版本: $JAVA_VERSION${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Java 版本: $(java -version 2>&1 | head -n 1)${NC}"
    echo -e "${GREEN}✓ Maven 版本: $(mvn -version | head -n 1)${NC}"
    echo ""
}

# 构建项目
build_project() {
    echo -e "${BLUE}2. 构建项目...${NC}"
    echo "这可能需要几分钟..."
    
    mvn clean install -DskipTests -q
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 构建成功${NC}"
    else
        echo -e "${YELLOW}✗ 构建失败${NC}"
        exit 1
    fi
    echo ""
}

# 运行测试
run_tests() {
    echo -e "${BLUE}3. 运行测试...${NC}"
    
    mvn test -q
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 测试通过${NC}"
    else
        echo -e "${YELLOW}⚠ 部分测试失败（这可能是正常的）${NC}"
    fi
    echo ""
}

# 列出所有模块
list_modules() {
    echo -e "${BLUE}4. 项目模块：${NC}"
    echo ""
    echo "  📦 mcp-router-v3      - MCP 路由器（端口 8000）"
    echo "  📦 mcp-server-v3      - MCP Server 示例"
    echo "  📦 mcp-server-v4      - MCP Server 示例"
    echo "  📦 mcp-server-v6      - MCP Server 示例（最新）"
    echo "  📦 mcp-client         - MCP 客户端（端口 8080）"
    echo "  📦 spring-ai-alibaba  - AI Agent 框架"
    echo ""
}

# 显示下一步
show_next_steps() {
    echo -e "${BLUE}5. 下一步：${NC}"
    echo ""
    echo "  🚀 启动 MCP Server:"
    echo "     cd mcp-server-v6 && mvn spring-boot:run"
    echo ""
    echo "  🚀 启动 MCP Client:"
    echo "     cd mcp-client && mvn spring-boot:run"
    echo ""
    echo "  📚 查看文档:"
    echo "     cat docs/START_HERE.md"
    echo ""
    echo "  🤖 使用 AI 工作流:"
    echo "     cat .agent/workflows/add-mcp-server.md"
    echo ""
}

# 主函数
main() {
    check_requirements
    
    # 询问是否构建
    read -p "是否构建项目？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        build_project
        
        # 询问是否运行测试
        read -p "是否运行测试？(y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            run_tests
        fi
    fi
    
    list_modules
    show_next_steps
    
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}  演示完成！${NC}"
    echo -e "${GREEN}=========================================${NC}"
}

# 运行主函数
main
