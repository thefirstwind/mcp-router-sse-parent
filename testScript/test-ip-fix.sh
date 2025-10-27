#!/bin/bash

echo "🧪 Testing MCP Server V5 IP Address Fix"
echo "========================================"

# 1. 检查Nacos配置
echo "1. Checking Nacos configuration..."
curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v5&namespaceId=public&groupName=mcp-server" | jq '.' 2>/dev/null || echo "Failed to get service list from Nacos"

echo ""
echo "2. Checking MCP Server registration..."
curl -s "http://127.0.0.1:8065/actuator/health" | jq '.' 2>/dev/null || echo "Failed to get health check from MCP Server"

echo ""
echo "3. Testing MCP Router connection..."
curl -s "http://localhost:8052/mcp/router/tools/mcp-server-v5" | jq '.' 2>/dev/null || echo "Failed to connect to MCP Router"

echo ""
echo "4. Checking system properties..."
echo "Spring Cloud Client IP: $spring_cloud_client_ip_address"
echo "Nacos Client IP: $nacos_client_ip"
echo "Server Address: $server_address"

echo ""
echo "✅ Test completed!" 