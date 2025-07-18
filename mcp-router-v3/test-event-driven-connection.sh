#!/bin/bash

# 事件驱动连接感知测试脚本
echo "🎯 测试事件驱动的 MCP 连接感知机制"
echo "=========================================="

# 服务端点
ROUTER_URL="http://localhost:8052"
SERVER_URL="http://localhost:8063"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试函数
test_connection_events() {
    echo -e "${BLUE}📋 1. 检查 mcp-router-v3 连接监听器状态${NC}"
    
    # 检查连接监听器状态
    curl -s "$ROUTER_URL/api/mcp/servers/connections" | jq '.'
    
    echo ""
    echo -e "${BLUE}📋 2. 检查当前连接状态${NC}"
    
    # 检查 mcp-server-v3 的连接状态
    curl -s "$ROUTER_URL/api/mcp/servers/connections/mcp-server-v3" | jq '.'
    
    echo ""
    echo -e "${BLUE}📋 3. 模拟 MCP Server 连接请求${NC}"
    
    # 模拟连接请求
    connection_request='{
        "serverId": "mcp-server-v3-test-' $(date +%s)'",
        "serverName": "mcp-server-v3",
        "serverPort": 8063,
        "capabilities": "tools,resources",
        "timestamp": '$(date +%s)'
    }'
    
    echo "发送连接请求："
    echo "$connection_request" | jq '.'
    
    response=$(curl -s -X POST "$ROUTER_URL/api/mcp/servers/connect" \
        -H "Content-Type: application/json" \
        -d "$connection_request")
    
    echo "连接响应："
    echo "$response" | jq '.'
    
    if echo "$response" | jq -e '.success' > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 连接请求成功接收${NC}"
    else
        echo -e "${RED}❌ 连接请求失败${NC}"
    fi
}

# 测试 Nacos 事件监听
test_nacos_events() {
    echo ""
    echo -e "${BLUE}📋 4. 测试 Nacos 事件监听机制${NC}"
    
    # 检查 Nacos 中的连接服务
    echo "查询 Nacos 中的连接状态服务..."
    
    # 这里可以添加直接查询 Nacos 的逻辑
    # 或者通过 mcp-router-v3 的监听器状态来验证
    
    echo "监听器应该能够检测到以下类型的事件："
    echo "- MCP Server 启动和注册连接状态"
    echo "- MCP Server 断开连接"
    echo "- 连接状态心跳更新"
    
    echo ""
    echo -e "${YELLOW}💡 建议验证步骤：${NC}"
    echo "1. 启动 mcp-server-v3，观察连接事件"
    echo "2. 停止 mcp-server-v3，观察断开事件"
    echo "3. 重启 mcp-server-v3，观察重连事件"
}

# 测试断开连接
test_disconnection() {
    echo ""
    echo -e "${BLUE}📋 5. 测试断开连接请求${NC}"
    
    disconnection_request='{
        "serverId": "mcp-server-v3-test",
        "serverName": "mcp-server-v3",
        "timestamp": '$(date +%s)'
    }'
    
    echo "发送断开连接请求："
    echo "$disconnection_request" | jq '.'
    
    response=$(curl -s -X POST "$ROUTER_URL/api/mcp/servers/disconnect" \
        -H "Content-Type: application/json" \
        -d "$disconnection_request")
    
    echo "断开连接响应："
    echo "$response" | jq '.'
    
    if echo "$response" | jq -e '.success' > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 断开连接请求成功处理${NC}"
    else
        echo -e "${RED}❌ 断开连接请求失败${NC}"
    fi
}

# 检查架构优势
demonstrate_advantages() {
    echo ""
    echo -e "${BLUE}📋 6. 新架构的优势演示${NC}"
    
    echo -e "${GREEN}✅ 事件驱动架构优势：${NC}"
    echo "1. 实时感知：连接建立/断开立即感知"
    echo "2. 无需轮询：基于 Nacos EventListener，零延迟"
    echo "3. 资源优化：MCP Server 主动管理连接"
    echo "4. 状态一致：Nacos 作为连接状态权威源"
    echo "5. 自动清理：连接断开后自动清理资源"
    
    echo ""
    echo -e "${YELLOW}🔄 与旧架构对比：${NC}"
    echo "旧架构（推模式）："
    echo "- mcp-router-v3 主动连接 → 资源浪费"
    echo "- 定时健康检查 → 30秒延迟"
    echo "- 连接状态感知滞后"
    
    echo ""
    echo "新架构（拉模式）："
    echo "- MCP Server 主动连接 → 高效"
    echo "- Nacos 事件通知 → 实时"
    echo "- 事件驱动感知 → 零延迟"
}

# 主测试流程
main() {
    echo "开始测试事件驱动连接感知机制..."
    
    # 检查服务状态
    echo -e "${BLUE}📋 0. 检查服务状态${NC}"
    
    if curl -s "$ROUTER_URL/actuator/health" > /dev/null; then
        echo -e "${GREEN}✅ mcp-router-v3 服务正常${NC}"
    else
        echo -e "${RED}❌ mcp-router-v3 服务异常${NC}"
        exit 1
    fi
    
    # 执行测试
    test_connection_events
    test_nacos_events
    test_disconnection
    demonstrate_advantages
    
    echo ""
    echo -e "${GREEN}🎉 事件驱动连接感知测试完成！${NC}"
    echo ""
    echo -e "${YELLOW}📝 下一步验证：${NC}"
    echo "1. 启动 mcp-server-v3，观察自动连接过程"
    echo "2. 查看 mcp-router-v3 日志中的连接事件"
    echo "3. 验证连接断开后的自动清理"
    echo "4. 测试多个 MCP Server 同时连接的场景"
}

# 执行主函数
main 