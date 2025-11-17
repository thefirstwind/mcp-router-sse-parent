#!/bin/bash

# 测试 mcp-router-v3 的 initialize 功能
# 使用方式: ./test-initialize.sh

BASE_URL="http://localhost:8052"
SSE_ENDPOINT="${BASE_URL}/sse/mcp-server-v6"
MESSAGE_ENDPOINT="${BASE_URL}/mcp/message"

echo "=========================================="
echo "测试 MCP Router V3 Initialize 功能"
echo "=========================================="
echo ""

# 步骤 1: 建立 SSE 连接
echo "步骤 1: 建立 SSE 连接..."
echo "GET ${SSE_ENDPOINT}"
echo ""

SESSION_ID=""
ENDPOINT_URL=""

# 使用 curl 建立 SSE 连接并提取首条 data 行（macOS 兼容实现）
# 读取到第一条以 'data:' 开头的行后立即退出，避免长时间阻塞
SSE_RESPONSE=$(curl -s -N -H "Accept: text/event-stream" --max-time 5 "${SSE_ENDPOINT}" 2>/dev/null | awk '/^data:/{print; exit}')

echo "SSE 响应:"
echo "${SSE_RESPONSE}"
echo ""

# 从响应中提取 endpoint URL（去掉前缀 data:）
ENDPOINT_URL=$(echo "${SSE_RESPONSE}" | sed -E 's/^data:\s*//')

if [ -z "${ENDPOINT_URL}" ]; then
    echo "❌ 无法从 SSE 响应中提取 endpoint URL"
    echo "尝试手动解析..."
    
    # 尝试另一种方式提取
    ENDPOINT_URL=$(echo "${SSE_RESPONSE}" | grep "data:" | sed 's/^data:\s*//' | tr -d ' ')
fi

if [ -z "${ENDPOINT_URL}" ]; then
    echo "❌ 仍然无法提取 endpoint URL，使用默认格式"
    # 生成一个 sessionId
    SESSION_ID=$(uuidgen 2>/dev/null || echo "test-session-$(date +%s)")
    ENDPOINT_URL="${MESSAGE_ENDPOINT}?sessionId=${SESSION_ID}"
else
    # 从 endpoint URL 中提取 sessionId
	SESSION_ID=$(echo "${ENDPOINT_URL}" | sed -n 's/.*sessionId=\([^&]*\).*/\1/p')
    if [ -z "${SESSION_ID}" ]; then
        SESSION_ID="test-session-$(date +%s)"
        ENDPOINT_URL="${MESSAGE_ENDPOINT}?sessionId=${SESSION_ID}"
    fi
fi

echo "✅ 提取的 Endpoint URL: ${ENDPOINT_URL}"
echo "✅ 提取的 Session ID: ${SESSION_ID}"
echo ""

# 步骤 2: 发送 initialize 请求
echo "步骤 2: 发送 initialize 请求..."
echo "POST ${ENDPOINT_URL}"
echo ""

INITIALIZE_REQUEST='{
  "jsonrpc": "2.0",
  "id": "init-001",
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "roots": {
        "listChanged": true
      },
      "sampling": {}
    },
    "clientInfo": {
      "name": "test-client",
      "version": "1.0.0"
    }
  }
}'

echo "请求内容:"
echo "${INITIALIZE_REQUEST}" | jq '.' 2>/dev/null || echo "${INITIALIZE_REQUEST}"
echo ""

HTTP_STATUS_AND_BODY=$(curl -s -w "HTTP_STATUS:%{http_code}" -X POST "${ENDPOINT_URL}" \
  -H "Content-Type: application/json" \
  -d "${INITIALIZE_REQUEST}")
HTTP_STATUS=$(echo "${HTTP_STATUS_AND_BODY}" | sed -n 's/.*HTTP_STATUS:\([0-9][0-9][0-9]\)$/\1/p')
INITIALIZE_RESPONSE=$(echo "${HTTP_STATUS_AND_BODY}" | sed -E 's/HTTP_STATUS:[0-9]{3}$//')

echo "响应内容:"
echo "${INITIALIZE_RESPONSE}" | jq '.' 2>/dev/null || echo "${INITIALIZE_RESPONSE}"
echo ""

# 验证响应（兼容 202 Accepted + SSE 推送的模式）
if [ "${HTTP_STATUS}" = "202" ] && echo "${INITIALIZE_RESPONSE}" | grep -q '"status":"accepted"'; then
    echo "ℹ️ 服务返回 202 Accepted，准备通过 SSE 获取 JSON-RPC 响应..."
    SSE_WITH_SESSION="${SSE_ENDPOINT}?sessionId=${SESSION_ID}"
    echo "GET ${SSE_WITH_SESSION}"
    SSE_EVENT=$(curl -s -N -H "Accept: text/event-stream" --max-time 5 "${SSE_WITH_SESSION}" 2>/dev/null | awk '/^data:/{sub(/^data:[ ]*/,\"\",\$0); print; exit}')
    echo "SSE 推送内容:"
    echo "${SSE_EVENT}" | jq '.' 2>/dev/null || echo "${SSE_EVENT}"
    echo ""
    if echo "${SSE_EVENT}" | grep -q '"jsonrpc":"2.0"'; then
        PROTOCOL_VERSION=$(echo "${SSE_EVENT}" | jq -r '.result.protocolVersion' 2>/dev/null)
        SERVER_NAME=$(echo "${SSE_EVENT}" | jq -r '.result.serverInfo.name' 2>/dev/null)
        SERVER_VERSION=$(echo "${SSE_EVENT}" | jq -r '.result.serverInfo.version' 2>/dev/null)
        echo "✅ Initialize 请求成功（通过 SSE 收到响应）！"
        echo "   - Protocol Version: ${PROTOCOL_VERSION}"
        echo "   - Server Name: ${SERVER_NAME}"
        echo "   - Server Version: ${SERVER_VERSION}"
        echo ""
        echo "=========================================="
        echo "✅ 测试通过！Initialize 功能正常"
        echo "=========================================="
        exit 0
    else
        echo "❌ 未能通过 SSE 获取到符合 JSON-RPC 的响应"
        echo "=========================================="
        echo "❌ 测试失败"
        echo "=========================================="
        exit 1
    fi
fi

# 回退：直接校验 HTTP 响应是否为 JSON-RPC（后向兼容）
if echo "${INITIALIZE_RESPONSE}" | grep -q '"jsonrpc":"2.0"'; then
    PROTOCOL_VERSION=$(echo "${INITIALIZE_RESPONSE}" | jq -r '.result.protocolVersion' 2>/dev/null)
    SERVER_NAME=$(echo "${INITIALIZE_RESPONSE}" | jq -r '.result.serverInfo.name' 2>/dev/null)
    SERVER_VERSION=$(echo "${INITIALIZE_RESPONSE}" | jq -r '.result.serverInfo.version' 2>/dev/null)
    echo "✅ Initialize 请求成功（HTTP 直接返回）！"
    echo "   - Protocol Version: ${PROTOCOL_VERSION}"
    echo "   - Server Name: ${SERVER_NAME}"
    echo "   - Server Version: ${SERVER_VERSION}"
    echo ""
    echo "=========================================="
    echo "✅ 测试通过！Initialize 功能正常"
    echo "=========================================="
else
    echo "❌ 响应格式不正确或 HTTP 状态码异常（HTTP_STATUS: ${HTTP_STATUS}）"
    echo "=========================================="
    echo "❌ 测试失败"
    echo "=========================================="
    exit 1
fi
