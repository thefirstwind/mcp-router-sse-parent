#!/bin/bash

# 简单的会话管理测试脚本

ROUTER_URL="http://localhost:8052"
SERVICE_NAME="mcp-server-v6"
LOG_FILE="/Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/mcp-router-v3-session-test.log"

echo "=========================================="
echo "会话管理功能测试"
echo "=========================================="
echo ""

# 1. 建立 SSE 连接并提取 sessionId
echo "1. 建立 SSE 连接..."
SSE_OUTPUT=$(timeout 3 curl -s -N "${ROUTER_URL}/sse?serviceName=${SERVICE_NAME}" 2>&1 | head -20)

# 从 endpoint 事件中提取 sessionId
SESSION_ID=$(echo "$SSE_OUTPUT" | grep -o 'sessionId=[^"& ]*' | head -1 | cut -d'=' -f2)

if [ -z "$SESSION_ID" ]; then
    echo "❌ 无法从 SSE 响应中提取 sessionId"
    echo "SSE 响应前 10 行："
    echo "$SSE_OUTPUT" | head -10
    exit 1
fi

echo "✅ 提取到 sessionId: $SESSION_ID"
echo ""

# 2. 等待 SSE 连接完全建立
echo "2. 等待 SSE 连接完全建立..."
sleep 3
echo "✅ 等待完成"
echo ""

# 3. 发送 initialize 请求
echo "3. 发送 initialize 请求（使用正确的 sessionId）..."
INIT_REQUEST='{"jsonrpc":"2.0","id":"test-init-1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}'

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${ROUTER_URL}/mcp/${SERVICE_NAME}/message?sessionId=${SESSION_ID}" \
    -H "Content-Type: application/json" \
    -d "$INIT_REQUEST" 2>&1)

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)
RESPONSE_BODY=$(echo "$RESPONSE" | grep -v "HTTP_CODE")

echo "   响应状态码: $HTTP_CODE"
echo "   响应内容: $RESPONSE_BODY"
echo ""

if [ "$HTTP_CODE" = "202" ] || echo "$RESPONSE_BODY" | grep -q "accepted"; then
    echo "✅ initialize 请求已接受，响应将通过 SSE 发送"
else
    echo "⚠️  initialize 请求响应异常"
fi

echo ""

# 4. 等待响应
echo "4. 等待响应通过 SSE 到达..."
sleep 3
echo "✅ 等待完成"
echo ""

# 5. 检查日志
echo "5. 检查日志中的会话管理信息..."
if [ ! -f "$LOG_FILE" ]; then
    echo "⚠️  日志文件不存在: $LOG_FILE"
    exit 1
fi

# 检查错误
NO_SINK_ERRORS=$(grep -c "No SSE sink found\|SSE sink not found" "$LOG_FILE" 2>/dev/null || echo "0")
if [ "$NO_SINK_ERRORS" -gt 0 ]; then
    echo "❌ 发现 $NO_SINK_ERRORS 条 'No SSE sink found' 错误"
    echo "最近的错误："
    grep "No SSE sink found\|SSE sink not found" "$LOG_FILE" | tail -3
else
    echo "✅ 未发现 'No SSE sink found' 错误"
fi

# 检查成功日志
SINK_FOUND=$(grep -c "SSE sink found\|Successfully sent.*via SSE" "$LOG_FILE" 2>/dev/null || echo "0")
if [ "$SINK_FOUND" -gt 0 ]; then
    echo "✅ 发现 $SINK_FOUND 条 SSE sink 成功使用的日志"
    echo "最近的成功日志："
    grep "SSE sink found\|Successfully sent.*via SSE" "$LOG_FILE" | tail -3
fi

# 检查已注册的会话
echo ""
echo "6. 检查已注册的会话..."
REGISTERED=$(grep "Registered SSE sink\|Registered client session" "$LOG_FILE" | tail -5)
if [ -n "$REGISTERED" ]; then
    echo "✅ 已注册的会话："
    echo "$REGISTERED"
else
    echo "⚠️  未找到已注册的会话日志"
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="















