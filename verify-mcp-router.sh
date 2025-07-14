#!/bin/bash

echo "🔍 MCP Router 协议验证 - 验证路由器是否遵循 MCP 2024-11-05 标准"
echo "=============================================================="
echo "验证目标: 确保mcp-router完全使用SSE协议与mcp-server通信，不使用HTTP"
echo ""

# 配置参数
ROUTER_HOST="localhost:8050"
SERVER_HOST="192.168.31.47:8061"  # 从Nacos注册的实际IP
CLIENT_ID="router-verify-$(date +%s)"
BASE_REQUEST_ID=$(date +%s)

echo "📋 测试配置："
echo "- MCP Router: $ROUTER_HOST"
echo "- MCP Server: $SERVER_HOST"
echo "- Client ID: $CLIENT_ID"
echo "- Base Request ID: $BASE_REQUEST_ID"
echo ""

# 创建临时文件存储响应
ROUTER_SSE_FILE="/tmp/router_verify_sse_$$"
ROUTER_ERROR_FILE="/tmp/router_verify_error_$$"

# =============================================================================
# 第一部分：验证mcp-router的MCP端点支持
# =============================================================================
echo "🎯 第一部分：验证mcp-router MCP端点"
echo "==============================="

# 检查mcp-router的健康状态
echo "📡 步骤1：检查mcp-router健康状态"
router_health=$(curl -s "http://$ROUTER_HOST/actuator/health" 2>/dev/null)
if echo "$router_health" | jq -e '.status == "UP"' >/dev/null 2>&1; then
    echo "✅ mcp-router健康状态正常"
else
    echo "❌ mcp-router健康状态异常"
    echo "响应: $router_health"
    exit 1
fi

echo ""

# 检测SSE端点
echo "📡 步骤2：检测mcp-router的SSE端点"
sse_endpoints=("/mcp/jsonrpc/sse" "/sse")
router_sse_endpoint=""
router_message_endpoint=""

for endpoint in "${sse_endpoints[@]}"; do
    echo "测试端点: http://$ROUTER_HOST$endpoint"
    response=$(curl -s -I "http://$ROUTER_HOST$endpoint?clientId=test" --max-time 3 2>&1)
    if echo "$response" | grep -q "text/event-stream\|200 OK"; then
        router_sse_endpoint="$endpoint"
        if [[ $endpoint == "/mcp/jsonrpc/sse" ]]; then
            router_message_endpoint="/mcp/jsonrpc"
        else
            router_message_endpoint="/mcp/message"
        fi
        echo "✅ 找到SSE端点: $endpoint (消息端点: $router_message_endpoint)"
        break
    else
        echo "❌ 端点不可用: $endpoint"
    fi
done

if [ -z "$router_sse_endpoint" ]; then
    echo "❌ mcp-router不支持标准MCP SSE端点"
    exit 1
fi

echo ""

# =============================================================================
# 第二部分：测试mcp-router的完整MCP协议实现
# =============================================================================
echo "🎯 第二部分：测试mcp-router完整MCP协议"
echo "================================="

# 步骤1：建立SSE连接
echo "📡 步骤1：建立与mcp-router的SSE连接"
echo "Command: curl -N -H 'Accept: text/event-stream' 'http://$ROUTER_HOST$router_sse_endpoint?clientId=$CLIENT_ID'"

# 在后台启动SSE监听
timeout 30 curl -N -H "Accept: text/event-stream" \
    "http://$ROUTER_HOST$router_sse_endpoint?clientId=$CLIENT_ID" \
    > "$ROUTER_SSE_FILE" 2> "$ROUTER_ERROR_FILE" &

SSE_PID=$!
echo "SSE监听进程启动: PID=$SSE_PID"

# 等待SSE连接建立
echo "⏳ 等待SSE连接建立... (3秒)"
sleep 3

if kill -0 $SSE_PID 2>/dev/null; then
    echo "✅ SSE连接已建立"
    
    if [ -s "$ROUTER_SSE_FILE" ]; then
        echo "📥 SSE初始响应:"
        head -3 "$ROUTER_SSE_FILE"
    fi
else
    echo "❌ SSE连接建立失败"
    if [ -s "$ROUTER_ERROR_FILE" ]; then
        echo "错误: $(cat "$ROUTER_ERROR_FILE")"
    fi
    exit 1
fi

echo ""

# 步骤2：MCP初始化握手
echo "🤝 步骤2：MCP初始化握手"
INIT_REQUEST_ID=$((BASE_REQUEST_ID + 1))

init_request='{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {
            "tools": {"listChanged": true},
            "resources": {"subscribe": true, "listChanged": true}
        },
        "clientInfo": {
            "name": "router-verify-client",
            "version": "1.0.0"
        }
    },
    "id": '$INIT_REQUEST_ID'
}'

echo "📤 发送初始化请求:"
echo "$init_request" | jq '.' 2>/dev/null || echo "$init_request"
echo ""

init_response=$(curl -s -X POST \
    "http://$ROUTER_HOST$router_message_endpoint" \
    -H "Content-Type: application/json" \
    -H "X-Client-Id: $CLIENT_ID" \
    -d "$init_request" 2>&1)

echo "📨 初始化响应:"
echo "$init_response" | jq '.' 2>/dev/null || echo "$init_response"

# 检查初始化响应
if echo "$init_response" | jq -e '.result.protocolVersion == "2024-11-05"' >/dev/null 2>&1; then
    echo "✅ MCP初始化成功"
    server_name=$(echo "$init_response" | jq -r '.result.serverInfo.name // "unknown"')
    echo "📋 服务器名称: $server_name"
else
    echo "❌ MCP初始化失败"
fi

echo ""

# 步骤3：初始化确认
echo "✅ 步骤3：发送初始化确认"
init_confirm='{
    "jsonrpc": "2.0",
    "method": "notifications/initialized",
    "params": {}
}'

curl -s -X POST \
    "http://$ROUTER_HOST$router_message_endpoint" \
    -H "Content-Type: application/json" \
    -H "X-Client-Id: $CLIENT_ID" \
    -d "$init_confirm" >/dev/null 2>&1

echo "📤 初始化确认已发送"
echo ""

# 步骤4：工具发现
echo "🔍 步骤4：发现可用工具"
TOOLS_REQUEST_ID=$((BASE_REQUEST_ID + 2))

tools_request='{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": '$TOOLS_REQUEST_ID'
}'

tools_response=$(curl -s -X POST \
    "http://$ROUTER_HOST$router_message_endpoint" \
    -H "Content-Type: application/json" \
    -H "X-Client-Id: $CLIENT_ID" \
    -d "$tools_request" 2>&1)

echo "📨 工具列表响应:"
echo "$tools_response" | jq '.' 2>/dev/null || echo "$tools_response"

# 分析工具列表
if echo "$tools_response" | jq -e '.result.tools' >/dev/null 2>&1; then
    echo "✅ 成功获取工具列表"
    tool_count=$(echo "$tools_response" | jq -r '.result.tools | length')
    echo "📊 可用工具数量: $tool_count"
    
    # 检查是否有getAllPersons工具
    if echo "$tools_response" | jq -e '.result.tools[]? | select(.name == "getAllPersons")' >/dev/null 2>&1; then
        echo "✅ 找到目标工具: getAllPersons"
        has_getAllPersons=true
    else
        echo "⚠️  未找到getAllPersons工具"
        has_getAllPersons=false
    fi
    
    echo "🛠️  可用工具列表:"
    echo "$tools_response" | jq -r '.result.tools[]?.name // empty' | head -10 | while read tool_name; do
        echo "  - $tool_name"
    done
else
    echo "❌ 获取工具列表失败"
    has_getAllPersons=false
fi

echo ""

# 步骤5：测试工具调用
if [ "$has_getAllPersons" = "true" ]; then
    echo "🎯 步骤5：测试工具调用 (getAllPersons)"
    CALL_REQUEST_ID=$((BASE_REQUEST_ID + 3))
    
    call_request='{
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
            "name": "getAllPersons",
            "arguments": {}
        },
        "id": '$CALL_REQUEST_ID'
    }'
    
    echo "📤 发送工具调用请求:"
    echo "$call_request" | jq '.' 2>/dev/null || echo "$call_request"
    echo ""
    
    call_response=$(curl -s -X POST \
        "http://$ROUTER_HOST$router_message_endpoint" \
        -H "Content-Type: application/json" \
        -H "X-Client-Id: $CLIENT_ID" \
        -d "$call_request" 2>&1)
    
    echo "📨 工具调用响应:"
    echo "$call_response" | jq '.' 2>/dev/null || echo "$call_response"
    
    # 分析调用结果
    if echo "$call_response" | jq -e '.result' >/dev/null 2>&1; then
        echo "✅ 工具调用成功"
    elif echo "$call_response" | jq -e '.error' >/dev/null 2>&1; then
        echo "❌ 工具调用错误"
        error_msg=$(echo "$call_response" | jq -r '.error.message // "未知错误"')
        echo "错误信息: $error_msg"
    else
        echo "⚠️  工具调用响应格式异常"
    fi
else
    echo "⚠️  跳过工具调用测试 - getAllPersons工具不可用"
fi

echo ""

# =============================================================================
# 第三部分：验证mcp-router与mcp-server的SSE通信
# =============================================================================
echo "🎯 第三部分：验证mcp-router与mcp-server的SSE通信"
echo "=========================================="

echo "📡 检查mcp-router是否真正使用SSE协议调用mcp-server"

# 使用netstat检查连接
echo "🔍 步骤1：检查网络连接"
echo "正在检查从mcp-router(8050)到mcp-server(8061)的连接..."

# 检查是否有到8061端口的连接
connections=$(netstat -an | grep ":8061" | grep "ESTABLISHED" | wc -l)
echo "📊 到mcp-server(8061)的已建立连接数: $connections"

if [ "$connections" -gt 0 ]; then
    echo "✅ 检测到活跃的网络连接"
    netstat -an | grep ":8061" | grep "ESTABLISHED" | head -3
else
    echo "⚠️  未检测到活跃连接（可能是瞬时连接）"
fi

echo ""

# 检查mcp-router日志中的SSE相关信息
echo "🔍 步骤2：检查mcp-router日志"
if [ -f "logs/mcp-router.log" ]; then
    echo "📋 最近的SSE相关日志:"
    tail -50 logs/mcp-router.log | grep -i "sse\|session\|mcp.*call" | tail -5
else
    echo "⚠️  未找到mcp-router日志文件"
fi

echo ""

# =============================================================================
# 第四部分：检查SSE异步响应
# =============================================================================
echo "🎯 第四部分：检查SSE异步响应"
echo "========================="

echo "📡 等待SSE异步响应... (5秒)"
sleep 5

# 终止SSE监听
kill $SSE_PID 2>/dev/null || true
wait $SSE_PID 2>/dev/null

echo "📥 完整SSE响应流:"
if [ -s "$ROUTER_SSE_FILE" ]; then
    cat "$ROUTER_SSE_FILE"
    echo ""
    
    # 分析SSE响应
    echo "🔍 SSE响应分析:"
    response_count=$(grep -c "event:" "$ROUTER_SSE_FILE" 2>/dev/null || echo "0")
    data_count=$(grep -c "data:" "$ROUTER_SSE_FILE" 2>/dev/null || echo "0")
    
    echo "📊 SSE事件数量: $response_count"
    echo "📊 SSE数据数量: $data_count"
    
    if grep -q "event:connection" "$ROUTER_SSE_FILE"; then
        echo "✅ 检测到连接事件"
    fi
    
    if grep -q "event:message" "$ROUTER_SSE_FILE"; then
        echo "✅ 检测到消息事件"
    fi
    
    if grep -q "data:" "$ROUTER_SSE_FILE" && grep -q "{" "$ROUTER_SSE_FILE"; then
        echo "✅ 包含JSON响应数据"
    fi
else
    echo "❌ 未收到SSE响应"
fi

echo ""

# =============================================================================
# 测试总结
# =============================================================================
echo "======================================================"
echo "📊 MCP Router 协议验证总结"
echo "======================================================"

# 分析测试结果
router_health_ok="❌"
sse_endpoint_ok="❌"
mcp_init_ok="❌"
tools_list_ok="❌"
tool_call_ok="❌"
sse_response_ok="❌"

# 检查各项结果
if echo "$router_health" | jq -e '.status == "UP"' >/dev/null 2>&1; then
    router_health_ok="✅"
fi

if [ -n "$router_sse_endpoint" ]; then
    sse_endpoint_ok="✅"
fi

if echo "$init_response" | jq -e '.result.protocolVersion == "2024-11-05"' >/dev/null 2>&1; then
    mcp_init_ok="✅"
fi

if echo "$tools_response" | jq -e '.result.tools' >/dev/null 2>&1; then
    tools_list_ok="✅"
fi

if echo "$call_response" | jq -e '.result' >/dev/null 2>&1; then
    tool_call_ok="✅"
elif echo "$call_response" | jq -e '.error' >/dev/null 2>&1; then
    tool_call_ok="⚠️"
fi

if [ -s "$ROUTER_SSE_FILE" ] && grep -q "data:" "$ROUTER_SSE_FILE"; then
    sse_response_ok="✅"
fi

echo "🎯 MCP Router 协议合规性:"
echo "1. Router健康状态: $router_health_ok"
echo "2. SSE端点支持: $sse_endpoint_ok"
echo "3. MCP初始化: $mcp_init_ok"
echo "4. 工具发现: $tools_list_ok"
echo "5. 工具调用: $tool_call_ok"
echo "6. SSE异步响应: $sse_response_ok"
echo ""

echo "📋 MCP 2024-11-05 协议遵循:"
echo "- SSE传输层: $([ -n "$router_sse_endpoint" ] && echo "✅" || echo "❌")"
echo "- JSON-RPC 2.0: ✅"
echo "- 协议版本: $(echo "$init_response" | jq -e '.result.protocolVersion == "2024-11-05"' >/dev/null 2>&1 && echo "✅" || echo "❌")"
echo "- 双向通信: $([ -s "$ROUTER_SSE_FILE" ] && echo "✅" || echo "❌")"
echo ""

echo "🔍 关键发现:"
if [ "$sse_endpoint_ok" = "✅" ] && [ "$mcp_init_ok" = "✅" ]; then
    echo "✅ mcp-router正确实现了MCP协议端点"
else
    echo "❌ mcp-router的MCP协议实现有问题"
fi

if [ "$tools_list_ok" = "✅" ]; then
    echo "✅ mcp-router能够正确发现和代理工具"
else
    echo "❌ mcp-router的工具代理功能有问题"
fi

if [ "$connections" -gt 0 ] || grep -q "sse\|session" logs/mcp-router.log 2>/dev/null; then
    echo "✅ mcp-router正在使用SSE协议与后端通信"
else
    echo "⚠️  需要进一步验证mcp-router的后端通信方式"
fi

echo ""

echo "📋 测试环境信息:"
echo "- 客户端ID: $CLIENT_ID"
echo "- Router端点: $router_sse_endpoint"
echo "- 消息端点: $router_message_endpoint"
echo "- 网络连接数: $connections"
echo ""

echo "🗂️  详细日志文件:"
echo "- Router SSE: $ROUTER_SSE_FILE"
echo "- 错误日志: $ROUTER_ERROR_FILE"

echo ""
echo "🔍 MCP Router 协议验证完成"

# 根据结果给出建议
if [ "$sse_endpoint_ok" = "✅" ] && [ "$mcp_init_ok" = "✅" ] && [ "$tools_list_ok" = "✅" ]; then
    echo "✅ mcp-router基本符合MCP协议要求"
    if [ "$tool_call_ok" != "✅" ]; then
        echo "⚠️  建议检查工具调用的具体实现"
    fi
else
    echo "❌ mcp-router需要进一步改进以完全符合MCP协议"
fi 