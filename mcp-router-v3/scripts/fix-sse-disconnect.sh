#!/bin/bash

echo "=== 修复 SSE 连接自动断开问题 ==="
echo ""

# 1. 更新 Nginx 配置
echo "1. 检查 Nginx 配置..."
if grep -q "proxy_read_timeout 600s" nginx/nginx.conf; then
    echo "   ✅ Nginx 超时配置已更新"
else
    echo "   ⚠️  Nginx 超时配置需要更新"
    echo "   请检查 nginx/nginx.conf 中的 proxy_read_timeout"
fi
echo ""

# 2. 检查应用代码
echo "2. 检查应用代码..."
if grep -q 'event("heartbeat")' src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java; then
    echo "   ✅ 心跳机制已改进（使用数据事件）"
else
    echo "   ⚠️  心跳机制需要改进"
fi

if grep -q "Duration.ofSeconds(15)" src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java | grep -q "heartbeat"; then
    echo "   ✅ 心跳间隔已缩短到 15 秒"
else
    echo "   ⚠️  心跳间隔可能需要调整"
fi
echo ""

# 3. 重新加载 Nginx（如果配置已更新）
echo "3. 重新加载 Nginx..."
if sudo nginx -t -c "$(pwd)/nginx/nginx.conf" 2>&1 | grep -q "successful"; then
    echo "   ✅ Nginx 配置测试通过"
    read -p "   是否现在重新加载 Nginx？(y/n): " reload_nginx
    if [ "$reload_nginx" = "y" ] || [ "$reload_nginx" = "Y" ]; then
        sudo nginx -s reload -c "$(pwd)/nginx/nginx.conf"
        echo "   ✅ Nginx 已重新加载"
    fi
else
    echo "   ❌ Nginx 配置测试失败"
    sudo nginx -t -c "$(pwd)/nginx/nginx.conf"
fi
echo ""

# 4. 重启应用（如果需要）
echo "4. 应用代码更改需要重新编译和重启..."
echo "   如果修改了 Java 代码，请："
echo "   1. 重新编译: mvn clean package"
echo "   2. 重启应用实例"
echo ""

echo "=== 修复完成 ==="
echo ""
echo "建议测试："
echo "  ./scripts/diagnose-sse-disconnect.sh http://mcp-bridge.test/sse/mcp-server-v6 600"
echo ""
