#!/bin/bash
# 测试域名访问 vs IP 访问

echo "=== 测试 1: 通过 IP 直接访问 ==="
echo "curl -N -m 3 http://localhost:8051/sse/mcp-server-v6"
curl -N -m 3 http://localhost:8051/sse/mcp-server-v6 2>&1 | head -5
echo ""

echo "=== 测试 2: 通过域名访问 ==="
echo "curl -N -m 3 http://mcp-bridge.local/sse/mcp-server-v6"
curl -N -m 3 http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -5
echo ""

echo "=== 测试 3: 检查 Nginx 配置 ==="
if [ -f "/opt/homebrew/etc/nginx/servers/mcp-bridge.conf" ]; then
    echo "✅ Nginx 配置文件存在: /opt/homebrew/etc/nginx/servers/mcp-bridge.conf"
    echo "配置内容:"
    cat /opt/homebrew/etc/nginx/servers/mcp-bridge.conf | grep -A 5 "X-Forwarded"
else
    echo "❌ Nginx 配置文件不存在，需要复制配置文件"
    echo "执行: sudo cp $(pwd)/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf"
fi



