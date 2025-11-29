#!/bin/bash

# 快速会话管理测试 - 使用后台进程保持 SSE 连接

ROUTER_URL="http://localhost:8052"
SERVICE_NAME="mcp-server-v6"
LOG_FILE="/Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/mcp-router-v3-session-test.log"

echo "=========================================="
echo "会话管理功能快速测试"
echo "=========================================="
echo ""

# 1. 在后台启动 SSE 连接并提取 sessionId
echo "1. 建立 SSE 连接（后台）..."
(
    curl -s -N "${ROUTER_URL}/sse?serviceName=${SERVICE_NAME}" > /tmp/sse_output.txt 2>&1 &
    SSE_PID=$!
    sleep 2
    
    # 从输出中提取 sessionId
    if [ -f /tmp/sse_output.txt ]; then
        SESSION_ID=$(grep -o 'sessionId=[^"& ]*' /tmp/sse_output.txt | head -1 | cut -d'=' -f2)
        if [ -n "$SESSION_ID" ]; then
            echo "$SESSION_ID" > /tmp/session_id.txt
        fi
    fi
    
    # 保持连接打开
    wait $SSE_PID
) &
SSE_BG_PID=$!

# 等待 sessionId 提取
sleep 3
SESSION_ID=$(cat /tmp/session_id.txt 2>/dev/null)

if [ -z "$SESSION_ID" ]; then
    echo "❌ 无法提取 sessionId，尝试从日志中获取..."
    # 从日志中获取最新的 sessionId
    SESSION_ID=$(grep "Registered SSE sink for session" "$LOG_FILE" | tail -1 | grep -o 'sessionId=[^, ]*' | cut -d'=' -f2)
fi

if [ -z "$SESSION_ID" ]; then
    echo "❌ 无法获取 sessionId"
    kill $SSE_BG_PID 2>/dev/null
    exit 1
fi

echo "✅ 使用 sessionId: $SESSION_ID"
echo ""

# 2. 发送 initialize 请求
echo "2. 发送 initialize 请求..."
INIT_REQUEST='{"jsonrpc":"2.0","id":"test-init-quick","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}'

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${ROUTER_URL}/mcp/${SERVICE_NAME}/message?sessionId=${SESSION_ID}" \
    -H "Content-Type: application/json" \
    -d "$INIT_REQUEST" 2>&1)

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)
RESPONSE_BODY=$(echo "$RESPONSE" | grep -v "HTTP_CODE")

echo "   响应状态码: $HTTP_CODE"
if [ "$HTTP_CODE" = "202" ]; then
    echo "✅ 请求已接受，响应将通过 SSE 发送"
elif [ "$HTTP_CODE" = "200" ]; then
    echo "⚠️  返回 HTTP 200（可能回退到 HTTP 响应）"
else
    echo "❌ 响应异常"
fi
echo ""

# 3. 等待并检查日志
echo "3. 等待响应并检查日志..."
sleep 3

# 检查最近的日志
echo ""
echo "4. 检查会话管理日志..."
RECENT_LOGS=$(tail -50 "$LOG_FILE" | grep -E "sessionId=${SESSION_ID}" | tail -5)

if echo "$RECENT_LOGS" | grep -q "SSE sink found\|Successfully sent.*via SSE"; then
    echo "✅ 发现 SSE sink 成功使用的日志："
    echo "$RECENT_LOGS" | grep "SSE sink found\|Successfully sent"
elif echo "$RECENT_LOGS" | grep -q "No SSE sink found\|SSE sink not found"; then
    echo "❌ 发现 'No SSE sink found' 错误："
    echo "$RECENT_LOGS" | grep "No SSE sink found\|SSE sink not found"
else
    echo "ℹ️  未找到相关日志（可能使用了不同的 sessionId）"
fi

# 清理
kill $SSE_BG_PID 2>/dev/null
rm -f /tmp/sse_output.txt /tmp/session_id.txt

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="





