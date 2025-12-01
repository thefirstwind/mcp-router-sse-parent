#!/bin/bash
# 调试 Nginx 头传递

echo "=== 调试 Nginx 头传递 ==="
echo ""

echo "测试 1: 检查 Nginx 是否传递 X-Forwarded-Host 头"
echo "curl -v http://mcp-bridge.local/actuator/health 2>&1 | grep -i 'x-forwarded'"
echo ""

RESPONSE=$(curl -v http://mcp-bridge.local/actuator/health 2>&1)
FORWARDED_HEADERS=$(echo "${RESPONSE}" | grep -i "x-forwarded")

if [ -z "${FORWARDED_HEADERS}" ]; then
    echo "❌ 没有看到 X-Forwarded 头 - Nginx 可能没有传递这些头"
    echo ""
    echo "检查 Nginx 配置："
    grep -A 2 "X-Forwarded-Host" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf 2>/dev/null || echo "配置文件不存在或没有此配置"
else
    echo "✅ 看到 X-Forwarded 头："
    echo "${FORWARDED_HEADERS}"
fi

echo ""
echo "测试 2: 检查应用收到的头"
echo "查看应用日志中是否有 forwardedHost"
echo ""

echo "测试 3: 直接测试 SSE 连接"
echo "curl -N -m 3 http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -3"
echo ""

SSE_RESPONSE=$(timeout 3 curl -N http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -3)
if echo "${SSE_RESPONSE}" | grep -q "event:endpoint"; then
    echo "✅ SSE 连接成功"
    echo "${SSE_RESPONSE}"
else
    echo "❌ SSE 连接失败或超时"
    echo "${SSE_RESPONSE}"
fi

echo ""
echo "=== 建议 ==="
echo "如果 X-Forwarded 头没有传递，请："
echo "1. 确认 Nginx 配置已更新：cat /opt/homebrew/etc/nginx/servers/mcp-bridge.conf | grep X-Forwarded-Host"
echo "2. 重载 Nginx：sudo nginx -t && sudo nginx -s reload"
echo "3. 检查 Nginx 错误日志：tail -f /opt/homebrew/var/log/nginx/error.log"








