#!/bin/bash

# 测试 endpointName 到服务名的映射功能

ENDPOINT_NAME="data-analysis"
SERVICE_NAME="mcp-data-analysis"
# 注意：mcp-router-v3 默认运行在 8051 端口
ROUTER_URL="http://localhost:8051"

echo "=========================================="
echo "测试 endpointName 到服务名的映射"
echo "=========================================="
echo ""

# 1. 测试使用 endpointName 查询工具列表
echo "1. 测试使用 endpointName (${ENDPOINT_NAME}) 查询工具列表..."
echo "   URL: ${ROUTER_URL}/mcp/router/tools/${ENDPOINT_NAME}"
echo ""

RESPONSE1=$(curl -s -w "\nHTTP_CODE:%{http_code}" "${ROUTER_URL}/mcp/router/tools/${ENDPOINT_NAME}")
HTTP_CODE1=$(echo "$RESPONSE1" | grep "HTTP_CODE" | cut -d: -f2)
BODY1=$(echo "$RESPONSE1" | sed '/HTTP_CODE/d')

echo "   响应状态码: ${HTTP_CODE1}"
echo "   响应内容:"
echo "$BODY1" | jq '.' 2>/dev/null || echo "$BODY1"
echo ""

if echo "$BODY1" | grep -q "目标服务不可用"; then
    echo "   ❌ 失败: 返回 '目标服务不可用'"
    FAILED=true
elif echo "$BODY1" | jq -e '.tools' > /dev/null 2>&1; then
    TOOL_COUNT=$(echo "$BODY1" | jq '.tools | length')
    echo "   ✅ 成功: 找到 ${TOOL_COUNT} 个工具"
    FAILED=false
else
    echo "   ⚠️  未知响应格式"
    FAILED=true
fi

echo ""

# 2. 测试使用 MCP 服务名查询工具列表（对比）
echo "2. 测试使用 MCP 服务名 (${SERVICE_NAME}) 查询工具列表..."
echo "   URL: ${ROUTER_URL}/mcp/router/tools/${SERVICE_NAME}"
echo ""

RESPONSE2=$(curl -s -w "\nHTTP_CODE:%{http_code}" "${ROUTER_URL}/mcp/router/tools/${SERVICE_NAME}")
HTTP_CODE2=$(echo "$RESPONSE2" | grep "HTTP_CODE" | cut -d: -f2)
BODY2=$(echo "$RESPONSE2" | sed '/HTTP_CODE/d')

echo "   响应状态码: ${HTTP_CODE2}"
echo "   响应内容:"
echo "$BODY2" | jq '.' 2>/dev/null || echo "$BODY2"
echo ""

if echo "$BODY2" | grep -q "目标服务不可用"; then
    echo "   ❌ 失败: 返回 '目标服务不可用'"
    FAILED2=true
elif echo "$BODY2" | jq -e '.tools' > /dev/null 2>&1; then
    TOOL_COUNT2=$(echo "$BODY2" | jq '.tools | length')
    echo "   ✅ 成功: 找到 ${TOOL_COUNT2} 个工具"
    FAILED2=false
else
    echo "   ⚠️  未知响应格式"
    FAILED2=true
fi

echo ""

# 3. 检查服务发现
echo "3. 检查服务发现..."
echo "   URL: ${ROUTER_URL}/mcp/router/services?serviceGroup=mcp-server"
echo ""

SERVICES_RESPONSE=$(curl -s "${ROUTER_URL}/mcp/router/services?serviceGroup=mcp-server")
if echo "$SERVICES_RESPONSE" | jq -e ".servers[]? | select(.name == \"${SERVICE_NAME}\")" > /dev/null 2>&1; then
    echo "   ✅ 服务 '${SERVICE_NAME}' 已被发现"
    SERVER_INFO=$(echo "$SERVICES_RESPONSE" | jq ".servers[] | select(.name == \"${SERVICE_NAME}\")")
    echo "   服务信息:"
    echo "$SERVER_INFO" | jq '.' | sed 's/^/     /'
else
    echo "   ❌ 服务 '${SERVICE_NAME}' 未被发现"
    echo "   已发现的服务:"
    echo "$SERVICES_RESPONSE" | jq -r '.servers[]?.name // empty' | sed 's/^/     - /'
fi

echo ""

# 4. 总结
echo "=========================================="
echo "测试总结"
echo "=========================================="
echo ""

if [ "$FAILED" = "false" ] && [ "$FAILED2" = "false" ]; then
    echo "✅ 测试通过: 两种方式都能成功查询工具列表"
    echo ""
    echo "说明:"
    echo "  - endpointName (${ENDPOINT_NAME}) 可以正确映射到 MCP 服务名 (${SERVICE_NAME})"
    echo "  - 缓存机制正常工作"
    exit 0
elif [ "$FAILED" = "true" ] && [ "$FAILED2" = "false" ]; then
    echo "⚠️  部分失败:"
    echo "  - endpointName (${ENDPOINT_NAME}) 查询失败"
    echo "  - MCP 服务名 (${SERVICE_NAME}) 查询成功"
    echo ""
    echo "可能的原因:"
    echo "  1. 服务发现逻辑中的 mcp- 前缀映射未生效"
    echo "  2. 缓存键问题"
    echo "  3. 日志中可能有更多信息"
    exit 1
elif [ "$FAILED" = "false" ] && [ "$FAILED2" = "true" ]; then
    echo "⚠️  部分失败:"
    echo "  - endpointName (${ENDPOINT_NAME}) 查询成功"
    echo "  - MCP 服务名 (${SERVICE_NAME}) 查询失败"
    echo ""
    echo "这很奇怪，说明服务可能注册在不同的名称下"
    exit 1
else
    echo "❌ 测试失败: 两种方式都失败"
    echo ""
    echo "可能的原因:"
    echo "  1. 服务未正确注册到 Nacos"
    echo "  2. 服务组不匹配"
    echo "  3. 服务健康检查失败"
    echo ""
    echo "建议:"
    echo "  1. 检查 Nacos 控制台，确认服务是否注册"
    echo "  2. 检查 mcp-router-v3 日志"
    echo "  3. 运行诊断脚本: zk-mcp-parent/zkInfo/diagnose-data-analysis.sh"
    exit 1
fi

