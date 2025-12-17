#!/bin/bash

# 会话管理测试脚本
# 测试 SSE 连接和消息请求的时序问题

echo "=========================================="
echo "会话管理功能测试"
echo "=========================================="
echo ""

ROUTER_URL="http://localhost:8052"
SERVICE_NAME="mcp-server-v6"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "1. 建立 SSE 连接..."
SSE_RESPONSE=$(curl -s -N "${ROUTER_URL}/sse?serviceName=${SERVICE_NAME}" 2>&1 | head -5)
echo "$SSE_RESPONSE" | head -3

# 从响应中提取 sessionId
SESSION_ID=$(echo "$SSE_RESPONSE" | grep -oP 'sessionId=\K[^"]+' | head -1)

if [ -z "$SESSION_ID" ]; then
    # 尝试从 endpoint 事件中提取
    ENDPOINT_LINE=$(echo "$SSE_RESPONSE" | grep "data:.*sessionId=" | head -1)
    if [ -n "$ENDPOINT_LINE" ]; then
        SESSION_ID=$(echo "$ENDPOINT_LINE" | grep -oP 'sessionId=\K[^"]+' | head -1)
    fi
fi

if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}❌ 无法从 SSE 响应中提取 sessionId${NC}"
    echo "SSE 响应内容："
    echo "$SSE_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✅ 提取到 sessionId: ${SESSION_ID}${NC}"
echo ""

# 等待一下，确保 SSE 连接完全建立
sleep 2

echo "2. 发送 initialize 请求..."
INIT_REQUEST='{"jsonrpc":"2.0","id":"test-init-1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}'

INIT_RESPONSE=$(curl -s -X POST "${ROUTER_URL}/mcp/${SERVICE_NAME}/message?sessionId=${SESSION_ID}" \
    -H "Content-Type: application/json" \
    -d "$INIT_REQUEST" 2>&1)

echo "响应状态码: $(echo "$INIT_RESPONSE" | head -1)"
echo "响应内容: $(echo "$INIT_RESPONSE" | tail -1)"
echo ""

# 检查响应
if echo "$INIT_RESPONSE" | grep -q "accepted\|202"; then
    echo -e "${GREEN}✅ initialize 请求已接受，响应将通过 SSE 发送${NC}"
else
    echo -e "${YELLOW}⚠️  initialize 请求响应异常${NC}"
fi

echo ""
echo "3. 等待 3 秒，检查日志中的错误..."
sleep 3

echo ""
echo "4. 检查日志中的会话管理错误..."
LOG_FILE="/Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/mcp-router-v3-session-test.log"

if [ -f "$LOG_FILE" ]; then
    # 检查是否有 "No SSE sink found" 错误
    NO_SINK_ERRORS=$(grep -c "No SSE sink found\|SSE sink not found" "$LOG_FILE" 2>/dev/null || echo "0")
    
    if [ "$NO_SINK_ERRORS" -gt 0 ]; then
        echo -e "${RED}❌ 发现 $NO_SINK_ERRORS 条 'No SSE sink found' 错误${NC}"
        echo "最近的错误："
        grep "No SSE sink found\|SSE sink not found" "$LOG_FILE" | tail -3
    else
        echo -e "${GREEN}✅ 未发现 'No SSE sink found' 错误${NC}"
    fi
    
    # 检查是否有 "SSE sink found" 成功日志
    SINK_FOUND=$(grep -c "SSE sink found\|Successfully sent.*via SSE" "$LOG_FILE" 2>/dev/null || echo "0")
    if [ "$SINK_FOUND" -gt 0 ]; then
        echo -e "${GREEN}✅ 发现 $SINK_FOUND 条 SSE sink 成功使用的日志${NC}"
        echo "最近的成功日志："
        grep "SSE sink found\|Successfully sent.*via SSE" "$LOG_FILE" | tail -3
    fi
    
    # 检查已注册的 sessionId
    echo ""
    echo "5. 检查已注册的会话..."
    REGISTERED_SESSIONS=$(grep "Registered SSE sink\|Registered client session" "$LOG_FILE" | tail -5)
    if [ -n "$REGISTERED_SESSIONS" ]; then
        echo -e "${GREEN}✅ 已注册的会话：${NC}"
        echo "$REGISTERED_SESSIONS"
    else
        echo -e "${YELLOW}⚠️  未找到已注册的会话日志${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  日志文件不存在: $LOG_FILE${NC}"
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="















