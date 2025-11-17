#!/bin/bash

# 测试 mcp-router-v3 的 tools/list 功能（优先通过已绑定的 SSE session）
# 使用方式: ./test-tools-list.sh

BASE_URL="http://localhost:8052"
SSE_ENDPOINT="${BASE_URL}/sse/mcp-server-v6"
MESSAGE_ENDPOINT="${BASE_URL}/mcp/message"

echo "=========================================="
echo "测试 MCP Router V3 tools/list 功能"
echo "=========================================="
echo ""

echo "步骤 1: 建立 SSE 连接并获取 sessionId..."
echo "GET ${SSE_ENDPOINT}"
echo ""

SESSION_ID=""
ENDPOINT_URL=""

# 建立 SSE 连接，读取首条 data 行，获取路由返回的消息端点（包含 sessionId）
SSE_RESPONSE=$(curl -s -N -H "Accept: text/event-stream" --max-time 5 "${SSE_ENDPOINT}" 2>/dev/null | awk '/^data:/{print; exit}')

echo "SSE 响应:"
echo "${SSE_RESPONSE}"
echo ""

ENDPOINT_URL=$(echo "${SSE_RESPONSE}" | sed -E 's/^data:\s*//')
if [ -z "${ENDPOINT_URL}" ]; then
  echo "❌ 无法从 SSE 响应中提取 endpoint URL"
  echo "尝试手动解析..."
  ENDPOINT_URL=$(echo "${SSE_RESPONSE}" | grep "data:" | sed 's/^data:\s*//' | tr -d ' ')
fi

if [ -z "${ENDPOINT_URL}" ]; then
  echo "❌ 仍然无法提取 endpoint URL，使用默认格式（未绑定服务，可能仅能处理路由内置方法）"
  SESSION_ID=$(uuidgen 2>/dev/null || echo "test-session-$(date +%s)")
  ENDPOINT_URL="${MESSAGE_ENDPOINT}?sessionId=${SESSION_ID}"
else
  SESSION_ID=$(echo "${ENDPOINT_URL}" | sed -n 's/.*sessionId=\([^&]*\).*/\1/p')
  if [ -z "${SESSION_ID}" ]; then
    SESSION_ID="test-session-$(date +%s)"
    ENDPOINT_URL="${MESSAGE_ENDPOINT}?sessionId=${SESSION_ID}"
  fi
fi

echo "✅ 使用的 Endpoint URL: ${ENDPOINT_URL}"
echo "✅ 使用的 Session ID: ${SESSION_ID}"
echo ""

echo "步骤 1.1: 维持 SSE 会话在线..."
SSE_WITH_SESSION="${SSE_ENDPOINT}?sessionId=${SESSION_ID}"
echo "GET ${SSE_WITH_SESSION}  (后台保持)"
curl -s -N -H "Accept: text/event-stream" "${SSE_WITH_SESSION}" >/dev/null 2>&1 &
SSE_BG_PID=$!
# 稍等片刻，确保会话注册完成
sleep 0.5

ROUTE_ENDPOINT_URL="${ENDPOINT_URL}"
case "${ROUTE_ENDPOINT_URL}" in
  *"serviceName="*)
    ;;
  *)
    ROUTE_ENDPOINT_URL="${ROUTE_ENDPOINT_URL}&serviceName=mcp-server-v6"
    ;;
esac

echo "步骤 2: 发送 tools/list 请求..."
echo "POST ${ROUTE_ENDPOINT_URL}"
echo ""

REQUEST_BODY='{
  "jsonrpc": "2.0",
  "id": "tools-list-001",
  "method": "tools/list",
  "params": {}
}'

echo "请求内容:"
echo "${REQUEST_BODY}" | jq '.' 2>/dev/null || echo "${REQUEST_BODY}"
echo ""

HTTP_STATUS_AND_BODY=$(curl -s -w "HTTP_STATUS:%{http_code}" -X POST "${ROUTE_ENDPOINT_URL}" \
  -H "Content-Type: application/json" \
  -d "${REQUEST_BODY}")
HTTP_STATUS=$(echo "${HTTP_STATUS_AND_BODY}" | sed -n 's/.*HTTP_STATUS:\([0-9][0-9][0-9]\)$/\1/p')
RESPONSE_BODY=$(echo "${HTTP_STATUS_AND_BODY}" | sed -E 's/HTTP_STATUS:[0-9]{3}$//')

echo "响应内容:"
echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
echo ""

# 处理 202 Accepted + SSE 推送场景
if [ "${HTTP_STATUS}" = "202" ] && echo "${RESPONSE_BODY}" | grep -q '"status":"accepted"'; then
  echo "ℹ️ 服务返回 202 Accepted，准备通过 SSE 获取 JSON-RPC 响应..."
  SSE_WITH_SESSION="${SSE_ENDPOINT}?sessionId=${SESSION_ID}"
  echo "GET ${SSE_WITH_SESSION}"
  SSE_EVENT=$(curl -s -N -H "Accept: text/event-stream" --max-time 5 "${SSE_WITH_SESSION}" 2>/dev/null | awk '/^data:/{sub(/^data:[ ]*/, "", $0); print; exit}')
  echo "SSE 推送内容:"
  echo "${SSE_EVENT}" | jq '.' 2>/dev/null || echo "${SSE_EVENT}"
  echo ""
  if echo "${SSE_EVENT}" | grep -q '"jsonrpc":"2.0"'; then
    if echo "${SSE_EVENT}" | jq -e '.result.tools' >/dev/null 2>&1; then
      TOOL_COUNT=$(echo "${SSE_EVENT}" | jq -r '.result.tools | length' 2>/dev/null)
      echo "✅ tools/list 成功（通过 SSE 收到响应），工具数量: ${TOOL_COUNT}"
      echo "=========================================="
      echo "✅ 测试通过！tools/list 功能正常"
      echo "=========================================="
      exit 0
    fi
  fi
  echo "❌ 未能通过 SSE 获取有效的 tools 列表"
  echo "=========================================="
  echo "❌ 测试失败"
  echo "=========================================="
  if [ -n "${SSE_BG_PID}" ]; then kill "${SSE_BG_PID}" >/dev/null 2>&1; fi
  exit 1
fi

# 回退：HTTP 直接返回 JSON-RPC
if echo "${RESPONSE_BODY}" | grep -q '"jsonrpc":"2.0"'; then
  if echo "${RESPONSE_BODY}" | jq -e '.result.tools' >/dev/null 2>&1; then
    TOOL_COUNT=$(echo "${RESPONSE_BODY}" | jq -r '.result.tools | length' 2>/dev/null)
    echo "✅ tools/list 成功（HTTP 直接返回），工具数量: ${TOOL_COUNT}"
    echo "=========================================="
    echo "✅ 测试通过！tools/list 功能正常"
    echo "=========================================="
    if [ -n "${SSE_BG_PID}" ]; then kill "${SSE_BG_PID}" >/dev/null 2>&1; fi
    exit 0
  else
    echo "❌ 响应中不存在 result.tools 字段"
    echo "=========================================="
    echo "❌ 测试失败"
    echo "=========================================="
    if [ -n "${SSE_BG_PID}" ]; then kill "${SSE_BG_PID}" >/dev/null 2>&1; fi
    exit 1
  fi
else
  echo "❌ 响应格式不正确或 HTTP 状态码异常（HTTP_STATUS: ${HTTP_STATUS}）"
  echo "=========================================="
  echo "❌ 测试失败"
  echo "=========================================="
  if [ -n "${SSE_BG_PID}" ]; then kill "${SSE_BG_PID}" >/dev/null 2>&1; fi
  exit 1
fi

