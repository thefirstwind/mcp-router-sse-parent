#!/bin/bash

# 测试多服务组发现功能
# 检查 mcp-router-v3 是否能正确发现不同组中的 MCP 服务

echo "🚀 测试多服务组发现功能"
echo "================================"

# 服务端点
ROUTER_URL="http://localhost:8052"

echo "📋 1. 检查路由器服务健康状态"
curl -s "$ROUTER_URL/actuator/health" | jq '.'

echo ""
echo "📊 2. 获取健康检查统计信息"
curl -s "$ROUTER_URL/mcp/health/stats" | jq '.'

echo ""
echo "🔍 3. 查询所有健康服务（通配符查询）"
curl -s "$ROUTER_URL/mcp/servers/healthy?serviceName=*&serviceGroup=*" | jq '.'

echo ""
echo "🏥 4. 手动触发全量健康检查"
curl -s -X POST "$ROUTER_URL/mcp/health/check-all"

echo ""
echo "📈 5. 再次查看统计信息（检查是否有变化）"
curl -s "$ROUTER_URL/mcp/health/stats" | jq '.'

echo ""
echo "🔍 6. 查询不同服务组的服务实例"
echo "▶️ 查询 mcp-server 组："
curl -s "$ROUTER_URL/mcp/servers/healthy?serviceName=*&serviceGroup=mcp-server" | jq '.'

echo ""
echo "▶️ 查询 mcp-server-v2 组："
curl -s "$ROUTER_URL/mcp/servers/healthy?serviceName=*&serviceGroup=mcp-server-v2" | jq '.'

echo ""
echo "▶️ 查询 DEFAULT_GROUP 组："
curl -s "$ROUTER_URL/mcp/servers/healthy?serviceName=*&serviceGroup=DEFAULT_GROUP" | jq '.'

echo ""
echo "🎯 7. 测试智能工具发现"
curl -s "$ROUTER_URL/api/v1/tools/discover" | jq '.'

echo ""
echo "✅ 测试完成！"
echo ""
echo "📝 说明："
echo "- 如果看到多个不同组的服务，说明多服务组发现功能正常"
echo "- 如果统计信息显示发现了服务，说明健康检查功能正常"
echo "- 如果智能工具发现返回了工具列表，说明工具路由功能正常" 