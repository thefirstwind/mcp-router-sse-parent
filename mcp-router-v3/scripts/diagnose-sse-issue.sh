#!/bin/bash
# 全面诊断 SSE 连接问题

echo "=== SSE 连接问题诊断 ==="
echo ""

# 1. 检查 Nginx 配置
echo "1. 检查 Nginx 配置..."
if [ -f "/opt/homebrew/etc/nginx/servers/mcp-bridge.conf" ]; then
    echo "✅ Nginx 配置文件存在"
    X_FORWARDED=$(grep -c "X-Forwarded-Host" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf)
    if [ "$X_FORWARDED" -gt 0 ]; then
        echo "✅ X-Forwarded-Host 配置存在"
    else
        echo "❌ X-Forwarded-Host 配置缺失"
    fi
else
    echo "❌ Nginx 配置文件不存在"
fi

echo ""

# 2. 检查应用端口
echo "2. 检查应用端口..."
for port in 8051 8052 8053; do
    if lsof -ti:$port >/dev/null 2>&1; then
        echo "✅ 端口 $port 正在监听"
    else
        echo "❌ 端口 $port 未监听"
    fi
done

echo ""

# 3. 测试直接访问
echo "3. 测试直接访问应用..."
RESPONSE1=$(timeout 2 curl -N -s http://localhost:8051/sse/mcp-server-v6 2>&1 | head -2)
if echo "$RESPONSE1" | grep -q "event:endpoint"; then
    echo "✅ 直接访问成功"
else
    echo "❌ 直接访问失败"
    echo "响应: $RESPONSE1"
fi

echo ""

# 4. 测试通过域名访问
echo "4. 测试通过域名访问..."
RESPONSE2=$(timeout 2 curl -N -s http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -2)
if echo "$RESPONSE2" | grep -q "event:endpoint"; then
    echo "✅ 域名访问成功"
else
    echo "❌ 域名访问失败"
    echo "响应: $RESPONSE2"
fi

echo ""

# 5. 检查应用日志
echo "5. 检查应用日志（最近 10 条 SSE 相关）..."
RECENT_LOGS=$(tail -100 logs/router-8051.log 2>&1 | grep -E "(SSE connection|Building base URL|Host=|forwardedHost)" | tail -10)
if [ -n "$RECENT_LOGS" ]; then
    echo "最近的日志："
    echo "$RECENT_LOGS"
else
    echo "⚠️  没有找到相关日志"
fi

echo ""

# 6. 检查 Nginx 错误日志
echo "6. 检查 Nginx 错误日志（最近 5 条）..."
NGINX_ERRORS=$(tail -20 /opt/homebrew/var/log/nginx/error.log 2>&1 | tail -5)
if [ -n "$NGINX_ERRORS" ]; then
    echo "Nginx 错误："
    echo "$NGINX_ERRORS"
else
    echo "✅ 没有 Nginx 错误"
fi

echo ""

# 总结
echo "=== 诊断总结 ==="
if echo "$RESPONSE1" | grep -q "event:endpoint" && echo "$RESPONSE2" | grep -q "event:endpoint"; then
    echo "✅ 所有测试通过！"
elif echo "$RESPONSE1" | grep -q "event:endpoint"; then
    echo "⚠️  直接访问正常，但域名访问失败"
    echo "   可能原因："
    echo "   1. Nginx 配置未生效（需要重载：sudo nginx -s reload）"
    echo "   2. Nginx 没有正确传递 X-Forwarded-Host 头"
    echo "   3. Nginx SSE 配置有问题"
else
    echo "❌ 直接访问也失败，应用可能有问题"
fi











