#!/bin/bash

echo "=== Testing appName Setting in mcp-router-v3 ==="

# 等待应用启动
echo "Waiting for application to start..."
sleep 10

# 测试配置发布
echo "Testing config publication with appName..."

# 发布一个测试配置
curl -X POST "http://localhost:8052/mcp/servers/config/publish" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-server-001",
    "name": "test-mcp-server",
    "version": "1.0.0",
    "description": "测试服务器，用于验证 appName 设置",
    "enabled": true,
    "host": "localhost",
    "port": 8080,
    "protocol": "http",
    "healthCheckPath": "/health",
    "metadata": {
      "tools.names": "test_tool_1,test_tool_2"
    }
  }'

echo ""
echo "Config published. Check Nacos console to verify appName appears as 'mcp-router-v3' in configuration history."
echo ""
echo "Steps to verify:"
echo "1. Open Nacos console: http://localhost:8848/nacos"
echo "2. Go to Configuration Management -> Configuration List"
echo "3. Find the published configuration (should start with 'test-server-001-1.0.0-mcp-server')"
echo "4. Click 'Details' or 'History' to check if 'appName' field shows 'mcp-router-v3'"
echo "5. Check if 'srcUser' or '应用归属' field shows 'mcp-router-v3'"
echo ""
echo "=== Test Complete ===" 