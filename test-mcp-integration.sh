#!/bin/bash

# MCP协议集成测试脚本

echo "=== MCP协议集成测试 ==="
echo "测试时间: $(date)"

# 等待服务启动
echo "等待服务启动..."
sleep 10

# 测试mcp-client获取工具列表
echo ""
echo "1. 测试获取工具列表"
curl -s "http://localhost:8070/mcp-client/api/v1/tools/list" | jq '.'

# 测试调用getAllPersons工具
echo ""
echo "2. 测试调用getAllPersons工具"
curl -s -X POST "http://localhost:8070/mcp-client/api/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{"toolName": "getAllPersons", "arguments": {}}' | jq '.'

# 测试调用addPerson工具
echo ""
echo "3. 测试调用addPerson工具"
curl -s -X POST "http://localhost:8070/mcp-client/api/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "addPerson", 
    "arguments": {
      "firstName": "John",
      "lastName": "Doe", 
      "age": 30,
      "nationality": "USA",
      "gender": "MALE"
    }
  }' | jq '.'

# 再次测试getAllPersons查看添加结果
echo ""
echo "4. 再次测试getAllPersons查看添加结果"
curl -s -X POST "http://localhost:8070/mcp-client/api/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{"toolName": "getAllPersons", "arguments": {}}' | jq '.'

# 测试调用get_system_info工具
echo ""
echo "5. 测试调用get_system_info工具"
curl -s -X POST "http://localhost:8070/mcp-client/api/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{"toolName": "get_system_info", "arguments": {}}' | jq '.'

# 测试调用list_servers工具
echo ""
echo "6. 测试调用list_servers工具"
curl -s -X POST "http://localhost:8070/mcp-client/api/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{"toolName": "list_servers", "arguments": {}}' | jq '.'

# 测试获取服务器状态
echo ""
echo "7. 测试获取服务器状态"
curl -s "http://localhost:8070/mcp-client/api/v1/servers/status" | jq '.'

echo ""
echo "=== MCP协议集成测试完成 ===" 