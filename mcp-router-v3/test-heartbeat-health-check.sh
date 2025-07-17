#!/bin/bash

# 测试基于心跳的健康检查功能
# 验证 HealthCheckService 是否正确使用心跳请求检查 MCP 服务健康状态

echo "🫀 测试基于心跳的健康检查功能"
echo "================================"

# 服务端点
ROUTER_URL="http://localhost:8052"

echo "📋 1. 检查路由器服务健康状态"
curl -s "$ROUTER_URL/actuator/health" | jq '.'

echo ""
echo "🔍 2. 查看当前发现的服务（应该使用新的心跳检查）"
curl -s "$ROUTER_URL/mcp/servers/healthy?serviceName=*&serviceGroup=*" | jq '.'

echo ""
echo "🏥 3. 手动触发心跳健康检查"
echo "触发全量健康检查..."
curl -s -X POST "$ROUTER_URL/mcp/health/check-all" | jq '.'

echo ""
echo "📊 4. 查看健康检查统计信息"
curl -s "$ROUTER_URL/mcp/health/stats" | jq '.'

echo ""
echo "🎯 5. 针对特定服务进行心跳检查"
echo "对 mcp-server-v2 进行心跳检查..."
curl -s -X POST "$ROUTER_URL/mcp/health/check/mcp-server-v2" | jq '.'

echo ""
echo "📈 6. 再次查看统计信息（验证心跳检查结果）"
curl -s "$ROUTER_URL/mcp/health/stats" | jq '.'

echo ""
echo "🔍 7. 查看服务发现结果"
curl -s "$ROUTER_URL/mcp/servers/healthy?serviceName=mcp-server-v2&serviceGroup=mcp-server" | jq '.'

echo ""
echo "📝 8. 检查日志中的心跳检查记录"
echo "检查最近的日志记录..."
tail -n 20 mcp-router-v3/logs/mcp-router-v3.log | grep -E "(heartbeat|Health check|SSE|MCP.*ping)"

echo ""
echo "✅ 心跳健康检查测试完成！"
echo ""
echo "📝 验证要点："
echo "- 健康检查应该发送 MCP ping 请求到 SSE 端点"
echo "- 不再使用 /actuator/health 端点"
echo "- 支持三层检查：心跳 -> SSE连接 -> 基础连接"
echo "- 统计信息应该显示正确的健康服务数量" 