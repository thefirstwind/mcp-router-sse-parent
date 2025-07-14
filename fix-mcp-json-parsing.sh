#!/bin/bash

echo "🔧 修复MCP JSON解析问题"
echo "====================="
echo "问题: mcp-router调用mcp-server时出现JSON解析错误"
echo "解决方案: 启用JSON注释支持和增强错误处理"
echo ""

# 检查当前mcp-router的配置
echo "📋 步骤1：检查当前mcp-router配置"
echo "正在查找JSON解析相关的配置..."

# 查找ObjectMapper配置
if find mcp-router -name "*.java" -exec grep -l "ObjectMapper\|JsonFactory" {} \; | head -1; then
    echo "✅ 找到JSON配置文件"
else
    echo "⚠️  需要添加JSON配置"
fi

echo ""

# 检查Spring AI MCP相关配置
echo "📋 步骤2：检查Spring AI MCP配置"
echo "查看mcp-router的application.yml配置..."

if [ -f "mcp-router/src/main/resources/application.yml" ]; then
    echo "当前配置:"
    grep -A 10 -B 5 "spring:" mcp-router/src/main/resources/application.yml | head -15
else
    echo "⚠️  未找到application.yml"
fi

echo ""

echo "📋 步骤3：建议的修复方案"
echo "=================="

echo "1. 在mcp-router中配置ObjectMapper启用注释支持"
echo "2. 增强mcp-server-v2的错误响应处理"
echo "3. 在解析响应前进行预处理"

echo ""
echo "🔧 是否应用修复? (需要手动确认)" 