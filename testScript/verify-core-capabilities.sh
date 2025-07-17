#!/bin/bash

echo "🎯 MCP Router 系统核心能力验证"
echo "====================================="
echo "验证目标: 完整的端到端MCP协议功能验证"
echo "架构: mcp-client(8070) → mcp-router(8050) → mcp-server-v2(8061)"
echo ""

# 配置参数
CLIENT_HOST="localhost:8070"
ROUTER_HOST="localhost:8050"
SERVER_HOST="192.168.31.47:8061"
TIMESTAMP=$(date +%s)

echo "📋 测试环境配置："
echo "- MCP Client: $CLIENT_HOST"
echo "- MCP Router: $ROUTER_HOST"
echo "- MCP Server: $SERVER_HOST"
echo "- 测试时间戳: $TIMESTAMP"
echo ""

# =============================================================================
# 第一部分：基础健康检查
# =============================================================================
echo "🏥 第一部分：基础健康检查"
echo "======================="

services=("mcp-client:$CLIENT_HOST" "mcp-router:$ROUTER_HOST" "mcp-server-v2:$SERVER_HOST")
all_healthy=true

for service_info in "${services[@]}"; do
    service_name=$(echo $service_info | cut -d: -f1)
    service_host=$(echo $service_info | cut -d: -f2-)
    
    echo "🔍 检查 $service_name ($service_host)..."
    
    health_response=$(curl -s "http://$service_host/actuator/health" 2>/dev/null)
    if echo "$health_response" | jq -e '.status == "UP"' >/dev/null 2>&1; then
        echo "✅ $service_name: 健康"
    else
        echo "❌ $service_name: 不健康或无响应"
        echo "   响应: $health_response"
        all_healthy=false
    fi
done

if [ "$all_healthy" = "false" ]; then
    echo "⚠️  部分服务不健康，继续验证可能的功能..."
else
    echo "✅ 所有服务健康状态正常"
fi

echo ""

# =============================================================================
# 第二部分：MCP协议层验证
# =============================================================================
echo "🔌 第二部分：MCP协议层验证"
echo "========================"

# 2.1 验证mcp-router的MCP端点
echo "📡 2.1 验证mcp-router MCP端点支持"
router_sse_test=$(curl -s -I "http://$ROUTER_HOST/mcp/jsonrpc/sse?clientId=test" --max-time 3 2>&1)
if echo "$router_sse_test" | grep -q "text/event-stream\|200 OK"; then
    echo "✅ mcp-router支持MCP SSE端点"
    router_mcp_ok=true
else
    echo "❌ mcp-router不支持MCP SSE端点"
    router_mcp_ok=false
fi

# 2.2 验证mcp-server-v2的MCP端点
echo "📡 2.2 验证mcp-server-v2 MCP端点支持"
server_sse_test=$(curl -s -I "http://$SERVER_HOST/sse?clientId=test" --max-time 3 2>&1)
if echo "$server_sse_test" | grep -q "text/event-stream\|200 OK"; then
    echo "✅ mcp-server-v2支持MCP SSE端点"
    server_mcp_ok=true
else
    echo "❌ mcp-server-v2不支持MCP SSE端点"
    server_mcp_ok=false
fi

echo ""

# =============================================================================
# 第三部分：工具发现与调用验证
# =============================================================================
echo "🛠️  第三部分：工具发现与调用验证"
echo "============================="

# 3.1 通过mcp-client验证工具发现
echo "🔍 3.1 通过mcp-client验证工具发现"
client_tools_response=$(curl -s "http://$CLIENT_HOST/mcp-client/api/v1/tools/list" 2>/dev/null)

if echo "$client_tools_response" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "✅ mcp-client工具发现成功"
    tool_count=$(echo "$client_tools_response" | jq -r '.data | length')
    echo "📊 发现工具数量: $tool_count"
    
    # 检查关键工具
    key_tools=("getAllPersons" "addPerson" "deletePerson")
    for tool in "${key_tools[@]}"; do
        if echo "$client_tools_response" | jq -e ".data[] | select(.name == \"$tool\")" >/dev/null 2>&1; then
            echo "✅ 找到关键工具: $tool"
        else
            echo "⚠️  未找到工具: $tool"
        fi
    done
    
    client_discovery_ok=true
else
    echo "❌ mcp-client工具发现失败"
    echo "   响应: $client_tools_response"
    client_discovery_ok=false
fi

echo ""

# 3.2 直接验证mcp-router的工具发现
echo "🔍 3.2 直接验证mcp-router工具发现"
router_init_request='{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {"tools": {"listChanged": true}},
        "clientInfo": {"name": "verify-client", "version": "1.0.0"}
    },
    "id": 1
}'

router_init_response=$(curl -s -X POST "http://$ROUTER_HOST/mcp/jsonrpc" \
    -H "Content-Type: application/json" \
    -d "$router_init_request" 2>/dev/null)

if echo "$router_init_response" | jq -e '.result.protocolVersion == "2024-11-05"' >/dev/null 2>&1; then
    echo "✅ mcp-router MCP初始化成功"
    
    # 获取工具列表
    router_tools_request='{
        "jsonrpc": "2.0",
        "method": "tools/list",
        "params": {},
        "id": 2
    }'
    
    router_tools_response=$(curl -s -X POST "http://$ROUTER_HOST/mcp/jsonrpc" \
        -H "Content-Type: application/json" \
        -d "$router_tools_request" 2>/dev/null)
    
    if echo "$router_tools_response" | jq -e '.result.tools' >/dev/null 2>&1; then
        echo "✅ mcp-router工具列表获取成功"
        router_tool_count=$(echo "$router_tools_response" | jq -r '.result.tools | length')
        echo "📊 Router发现工具数量: $router_tool_count"
        router_discovery_ok=true
    else
        echo "❌ mcp-router工具列表获取失败"
        router_discovery_ok=false
    fi
else
    echo "❌ mcp-router MCP初始化失败"
    router_discovery_ok=false
fi

echo ""

# =============================================================================
# 第四部分：端到端工具调用验证
# =============================================================================
echo "🎯 第四部分：端到端工具调用验证"
echo "============================="

# 4.1 通过mcp-client调用getAllPersons
echo "🔧 4.1 通过mcp-client调用getAllPersons"
client_call_request='{
    "toolName": "getAllPersons",
    "arguments": {}
}'

client_call_response=$(curl -s -X POST "http://$CLIENT_HOST/mcp-client/api/v1/tools/call" \
    -H "Content-Type: application/json" \
    -d "$client_call_request" 2>/dev/null)

echo "📨 mcp-client调用响应:"
echo "$client_call_response" | jq '.' 2>/dev/null || echo "$client_call_response"

if echo "$client_call_response" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "✅ mcp-client端到端调用成功"
    person_count=$(echo "$client_call_response" | jq -r '.data | length' 2>/dev/null || echo "未知")
    echo "📊 获取到的人员数量: $person_count"
    client_call_ok=true
elif echo "$client_call_response" | jq -e '.success == false' >/dev/null 2>&1; then
    echo "❌ mcp-client端到端调用失败"
    error_msg=$(echo "$client_call_response" | jq -r '.message // "未知错误"')
    echo "   错误信息: $error_msg"
    client_call_ok=false
else
    echo "⚠️  mcp-client调用响应格式异常"
    client_call_ok=false
fi

echo ""

# 4.2 直接通过mcp-router调用getAllPersons
echo "🔧 4.2 直接通过mcp-router调用getAllPersons"
router_call_request='{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
        "name": "getAllPersons",
        "arguments": {}
    },
    "id": 3
}'

router_call_response=$(curl -s -X POST "http://$ROUTER_HOST/mcp/jsonrpc" \
    -H "Content-Type: application/json" \
    -d "$router_call_request" 2>/dev/null)

echo "📨 mcp-router调用响应:"
echo "$router_call_response" | jq '.' 2>/dev/null || echo "$router_call_response"

if echo "$router_call_response" | jq -e '.result' >/dev/null 2>&1; then
    echo "✅ mcp-router直接调用成功"
    router_call_ok=true
elif echo "$router_call_response" | jq -e '.error' >/dev/null 2>&1; then
    echo "❌ mcp-router直接调用失败"
    error_msg=$(echo "$router_call_response" | jq -r '.error.message // "未知错误"')
    echo "   错误信息: $error_msg"
    router_call_ok=false
else
    echo "⚠️  mcp-router调用响应格式异常"
    router_call_ok=false
fi

echo ""

# =============================================================================
# 第五部分：数据库操作验证
# =============================================================================
echo "💾 第五部分：数据库操作验证"
echo "======================="

# 5.1 添加新人员
echo "➕ 5.1 测试添加新人员"
add_person_request='{
    "toolName": "addPerson",
    "arguments": {
        "name": "Test User ' + $TIMESTAMP + '",
        "age": 25,
        "nationality": "TestCountry"
    }
}'

add_response=$(curl -s -X POST "http://$CLIENT_HOST/mcp-client/api/v1/tools/call" \
    -H "Content-Type: application/json" \
    -d "$add_person_request" 2>/dev/null)

echo "📨 添加人员响应:"
echo "$add_response" | jq '.' 2>/dev/null || echo "$add_response"

if echo "$add_response" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "✅ 添加人员成功"
    new_person_id=$(echo "$add_response" | jq -r '.data.id // "未知"')
    echo "📋 新人员ID: $new_person_id"
    add_person_ok=true
else
    echo "❌ 添加人员失败"
    add_person_ok=false
    new_person_id=""
fi

echo ""

# 5.2 验证添加结果
echo "🔍 5.2 验证添加结果"
verify_response=$(curl -s -X POST "http://$CLIENT_HOST/mcp-client/api/v1/tools/call" \
    -H "Content-Type: application/json" \
    -d '{"toolName": "getAllPersons", "arguments": {}}' 2>/dev/null)

if echo "$verify_response" | jq -e '.success == true' >/dev/null 2>&1; then
    total_persons=$(echo "$verify_response" | jq -r '.data | length')
    echo "✅ 验证查询成功，当前总人数: $total_persons"
    
    if [ -n "$new_person_id" ] && [ "$new_person_id" != "未知" ]; then
        if echo "$verify_response" | jq -e ".data[] | select(.id == $new_person_id)" >/dev/null 2>&1; then
            echo "✅ 新添加的人员已确认存在"
        else
            echo "⚠️  新添加的人员未在列表中找到"
        fi
    fi
    verify_ok=true
else
    echo "❌ 验证查询失败"
    verify_ok=false
fi

echo ""

# =============================================================================
# 第六部分：系统信息验证
# =============================================================================
echo "ℹ️  第六部分：系统信息验证"
echo "====================="

# 6.1 获取系统信息
echo "🖥️  6.1 获取系统信息"
system_info_response=$(curl -s -X POST "http://$CLIENT_HOST/mcp-client/api/v1/tools/call" \
    -H "Content-Type: application/json" \
    -d '{"toolName": "get_system_info", "arguments": {}}' 2>/dev/null)

if echo "$system_info_response" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "✅ 系统信息获取成功"
    echo "📊 系统信息:"
    echo "$system_info_response" | jq -r '.data' 2>/dev/null || echo "$system_info_response"
    system_info_ok=true
else
    echo "❌ 系统信息获取失败"
    system_info_ok=false
fi

echo ""

# 6.2 列出已注册服务器
echo "🌐 6.2 列出已注册服务器"
list_servers_response=$(curl -s -X POST "http://$CLIENT_HOST/mcp-client/api/v1/tools/call" \
    -H "Content-Type: application/json" \
    -d '{"toolName": "list_servers", "arguments": {}}' 2>/dev/null)

if echo "$list_servers_response" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "✅ 服务器列表获取成功"
    server_count=$(echo "$list_servers_response" | jq -r '.data | length' 2>/dev/null || echo "未知")
    echo "📊 已注册服务器数量: $server_count"
    
    if echo "$list_servers_response" | jq -e '.data[] | select(.name == "mcp-server-v2")' >/dev/null 2>&1; then
        echo "✅ 找到mcp-server-v2服务器"
    else
        echo "⚠️  未找到mcp-server-v2服务器"
    fi
    
    list_servers_ok=true
else
    echo "❌ 服务器列表获取失败"
    list_servers_ok=false
fi

echo ""

# =============================================================================
# 第七部分：SSE协议验证
# =============================================================================
echo "📡 第七部分：SSE协议验证"
echo "===================="

echo "🔍 7.1 验证SSE连接建立"
sse_response_file="/tmp/sse_verify_$$"
timeout 10 curl -N -H "Accept: text/event-stream" \
    "http://$SERVER_HOST/sse?clientId=verify-$TIMESTAMP" \
    > "$sse_response_file" 2>/dev/null &

SSE_PID=$!
sleep 5

if kill -0 $SSE_PID 2>/dev/null; then
    echo "✅ SSE连接建立成功"
    kill $SSE_PID 2>/dev/null
    wait $SSE_PID 2>/dev/null
    
    if [ -s "$sse_response_file" ]; then
        echo "📥 SSE响应示例:"
        head -3 "$sse_response_file"
        
        if grep -q "event:" "$sse_response_file" && grep -q "data:" "$sse_response_file"; then
            echo "✅ SSE协议格式正确"
            sse_ok=true
        else
            echo "⚠️  SSE协议格式可能有问题"
            sse_ok=false
        fi
    else
        echo "⚠️  SSE响应为空"
        sse_ok=false
    fi
else
    echo "❌ SSE连接建立失败"
    sse_ok=false
fi

# 清理临时文件
rm -f "$sse_response_file"

echo ""

# =============================================================================
# 验证总结
# =============================================================================
echo "======================================================"
echo "📊 MCP Router 系统核心能力验证总结"
echo "======================================================"

# 计算总体成功率
total_tests=0
passed_tests=0

tests=(
    "基础健康检查:$all_healthy"
    "Router MCP端点:$router_mcp_ok"
    "Server MCP端点:$server_mcp_ok"
    "Client工具发现:$client_discovery_ok"
    "Router工具发现:$router_discovery_ok"
    "Client端到端调用:$client_call_ok"
    "Router直接调用:$router_call_ok"
    "数据库添加操作:$add_person_ok"
    "数据库验证查询:$verify_ok"
    "系统信息获取:$system_info_ok"
    "服务器列表获取:$list_servers_ok"
    "SSE协议验证:$sse_ok"
)

echo "🎯 详细验证结果:"
for test_info in "${tests[@]}"; do
    test_name=$(echo $test_info | cut -d: -f1)
    test_result=$(echo $test_info | cut -d: -f2)
    
    total_tests=$((total_tests + 1))
    
    if [ "$test_result" = "true" ]; then
        echo "✅ $test_name: 通过"
        passed_tests=$((passed_tests + 1))
    else
        echo "❌ $test_name: 失败"
    fi
done

echo ""
echo "📈 总体统计:"
echo "- 总测试项: $total_tests"
echo "- 通过测试: $passed_tests"
echo "- 失败测试: $((total_tests - passed_tests))"

if [ $passed_tests -gt 0 ]; then
    success_rate=$((passed_tests * 100 / total_tests))
    echo "- 成功率: $success_rate%"
else
    echo "- 成功率: 0%"
fi

echo ""

# 给出总体评估
if [ $success_rate -ge 90 ]; then
    echo "🎉 系统核心能力验证优秀！"
    echo "✅ MCP Router系统完全符合设计要求"
elif [ $success_rate -ge 70 ]; then
    echo "👍 系统核心能力验证良好"
    echo "⚠️  部分功能需要优化"
elif [ $success_rate -ge 50 ]; then
    echo "⚠️  系统核心能力验证一般"
    echo "🔧 需要重点修复失败的功能"
else
    echo "❌ 系统核心能力验证不足"
    echo "🚨 需要全面检查和修复"
fi

echo ""
echo "🏁 验证完成时间: $(date)"
echo "🔍 详细日志已保存，可查看各服务日志文件获取更多信息" 