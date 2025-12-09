#!/bin/bash
# 快速验证 Nginx 配置和连接

echo "=== 验证 Nginx 配置 ==="
echo ""

# 检查配置文件
if [ -f "/opt/homebrew/etc/nginx/servers/mcp-bridge.conf" ]; then
    echo "✅ Nginx 配置文件存在"
    
    # 检查关键配置
    if grep -q "X-Forwarded-Host" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf; then
        echo "✅ X-Forwarded-Host 配置存在"
    else
        echo "❌ X-Forwarded-Host 配置缺失"
    fi
    
    if grep -q "proxy_buffering off" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf; then
        echo "✅ proxy_buffering off 配置存在"
    else
        echo "❌ proxy_buffering off 配置缺失"
    fi
    
    if grep -q "proxy_read_timeout 300s" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf; then
        echo "✅ proxy_read_timeout 300s 配置存在"
    else
        echo "❌ proxy_read_timeout 300s 配置缺失"
    fi
else
    echo "❌ Nginx 配置文件不存在"
    echo "   请运行: sudo cp $(pwd)/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf"
fi

echo ""
echo "=== 测试连接 ==="
echo ""

# 测试 1: 直接访问（带 X-Forwarded-Host 头）
echo "测试 1: 通过 localhost:8051 直接访问（模拟 Nginx 行为）"
RESPONSE1=$(curl -s -N -m 2 -H "X-Forwarded-Host: mcp-bridge.local" -H "X-Forwarded-Proto: http" http://localhost:8051/sse/mcp-server-v6 2>&1 | head -3)
if echo "${RESPONSE1}" | grep -q "event:endpoint"; then
    echo "✅ 测试 1 成功 - 应用能正确识别域名"
    echo "${RESPONSE1}" | head -2
else
    echo "❌ 测试 1 失败"
    echo "${RESPONSE1}"
fi

echo ""

# 测试 2: 通过域名访问
echo "测试 2: 通过域名访问（需要 Nginx 配置已重载）"
RESPONSE2=$(curl -s -N -m 3 http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -3)
if echo "${RESPONSE2}" | grep -q "event:endpoint"; then
    echo "✅ 测试 2 成功 - Nginx 配置已生效"
    echo "${RESPONSE2}" | head -2
else
    echo "⚠️  测试 2 失败或超时 - 可能需要重载 Nginx"
    echo "   请运行: sudo nginx -s reload"
    echo "${RESPONSE2}" | head -2
fi

echo ""
echo "=== 总结 ==="
if echo "${RESPONSE1}" | grep -q "event:endpoint" && echo "${RESPONSE2}" | grep -q "event:endpoint"; then
    echo "✅ 所有测试通过！Nginx 配置已正确生效。"
elif echo "${RESPONSE1}" | grep -q "event:endpoint"; then
    echo "⚠️  应用代码正常，但 Nginx 配置需要重载"
    echo "   运行: sudo nginx -s reload"
else
    echo "❌ 应用可能有问题，请检查日志"
fi











