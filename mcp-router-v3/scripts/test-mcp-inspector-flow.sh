#!/bin/bash
# 模拟 mcp inspector 的连接流程

BASE_URL="http://mcp-bridge.local"
SSE_ENDPOINT="${BASE_URL}/sse/mcp-server-v6"

echo "=== 测试 MCP Inspector 连接流程 ==="
echo ""

# 步骤 1: 建立 SSE 连接并获取 endpoint
echo "步骤 1: 建立 SSE 连接..."
SSE_RESPONSE=$(curl -s -N -m 5 "${SSE_ENDPOINT}" 2>&1 | head -5)

echo "SSE 响应:"
echo "${SSE_RESPONSE}"
echo ""

# 提取 endpoint URL 和 sessionId
ENDPOINT_URL=$(echo "${SSE_RESPONSE}" | grep "^data:" | head -1 | sed 's/^data://' | tr -d ' ')
SESSION_ID=$(echo "${ENDPOINT_URL}" | sed -n 's/.*sessionId=\([^&]*\).*/\1/p')

if [ -z "${ENDPOINT_URL}" ] || [ -z "${SESSION_ID}" ]; then
    echo "❌ 无法提取 endpoint URL 或 sessionId"
    exit 1
fi

echo "✅ Endpoint URL: ${ENDPOINT_URL}"
echo "✅ Session ID: ${SESSION_ID}"
echo ""

# 步骤 2: 在后台保持 SSE 连接
echo "步骤 2: 保持 SSE 连接..."
curl -s -N "${SSE_ENDPOINT}?sessionId=${SESSION_ID}" > /dev/null 2>&1 &
SSE_PID=$!
sleep 1
echo "✅ SSE 连接已建立 (PID: ${SSE_PID})"
echo ""

# 步骤 3: 发送 initialize 请求
echo "步骤 3: 发送 initialize 请求..."
INIT_REQUEST='{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {
            "tools": {"listChanged": true},
            "resources": {"listChanged": true},
            "prompts": {"listChanged": true}
        },
        "clientInfo": {
            "name": "mcp-inspector",
            "version": "1.0.0"
        }
    },
    "id": 1
}'

INIT_RESPONSE=$(curl -s -X POST "${ENDPOINT_URL}" \
    -H "Content-Type: application/json" \
    -d "${INIT_REQUEST}")

echo "Initialize 响应:"
echo "${INIT_RESPONSE}" | jq '.' 2>/dev/null || echo "${INIT_RESPONSE}"
echo ""

# 等待一下，让响应通过 SSE 发送
sleep 1

# 步骤 4: 发送 tools/list 请求
echo "步骤 4: 发送 tools/list 请求..."
TOOLS_LIST_REQUEST='{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": 2
}'

TOOLS_RESPONSE=$(curl -s -X POST "${ENDPOINT_URL}" \
    -H "Content-Type: application/json" \
    -d "${TOOLS_LIST_REQUEST}")

echo "Tools/list 响应:"
echo "${TOOLS_RESPONSE}" | jq '.' 2>/dev/null || echo "${TOOLS_RESPONSE}"
echo ""

# 等待一下，让响应通过 SSE 发送
sleep 2

# 检查 SSE 连接是否还在运行
if kill -0 ${SSE_PID} 2>/dev/null; then
    echo "✅ SSE 连接仍然活跃"
else
    echo "❌ SSE 连接已断开"
fi

# 清理
kill ${SSE_PID} 2>/dev/null

echo ""
echo "=== 测试完成 ==="












