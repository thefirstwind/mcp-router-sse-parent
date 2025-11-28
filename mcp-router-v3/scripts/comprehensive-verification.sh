#!/bin/bash
# 全面验证修复

echo "=== MCP Router 修复验证 ==="
echo ""

# 1. 检查 Nginx 配置
echo "1. 检查 Nginx 配置..."
if [ -f "/opt/homebrew/etc/nginx/servers/mcp-bridge.conf" ]; then
    echo "✅ Nginx 配置文件存在"
    
    X_FORWARDED_COUNT=$(grep -c "X-Forwarded-Host" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf)
    if [ "$X_FORWARDED_COUNT" -gt 0 ]; then
        echo "✅ X-Forwarded-Host 配置存在"
    else
        echo "❌ X-Forwarded-Host 配置缺失"
    fi
    
    PROXY_BUFFERING=$(grep -c "proxy_buffering off" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf)
    if [ "$PROXY_BUFFERING" -gt 0 ]; then
        echo "✅ proxy_buffering off 配置存在"
    else
        echo "❌ proxy_buffering off 配置缺失"
    fi
else
    echo "❌ Nginx 配置文件不存在"
fi

echo ""

# 2. 检查 Nginx 进程
echo "2. 检查 Nginx 进程..."
NGINX_COUNT=$(ps aux | grep nginx | grep -v grep | wc -l | tr -d ' ')
if [ "$NGINX_COUNT" -gt 0 ]; then
    echo "✅ Nginx 正在运行 (${NGINX_COUNT} 个进程)"
else
    echo "❌ Nginx 未运行"
fi

echo ""

# 3. 测试健康检查
echo "3. 测试健康检查端点..."
HEALTH_RESPONSE=$(curl -s http://mcp-bridge.local/actuator/health)
if echo "$HEALTH_RESPONSE" | grep -q "UP"; then
    echo "✅ 健康检查通过"
else
    echo "❌ 健康检查失败"
    echo "响应: $HEALTH_RESPONSE"
fi

echo ""

# 4. 测试 SSE 连接
echo "4. 测试 SSE 连接..."
SSE_RESPONSE=$(timeout 3 curl -N -s http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -3)
if echo "$SSE_RESPONSE" | grep -q "event:endpoint"; then
    echo "✅ SSE 连接成功"
    echo "$SSE_RESPONSE" | head -2
    SESSION_ID=$(echo "$SSE_RESPONSE" | grep "data:" | sed 's/.*sessionId=\([^&]*\).*/\1/')
    if [ -n "$SESSION_ID" ]; then
        echo "✅ 成功提取 sessionId: ${SESSION_ID:0:8}..."
    fi
else
    echo "❌ SSE 连接失败或超时"
    echo "响应: $SSE_RESPONSE"
fi

echo ""

# 5. 检查应用日志
echo "5. 检查应用日志（最近 5 条 SSE 相关日志）..."
RECENT_LOGS=$(tail -100 logs/router-8051.log 2>&1 | grep -E "(forwardedHost|Built base URL|SSE connection)" | tail -5)
if [ -n "$RECENT_LOGS" ]; then
    echo "最近的日志："
    echo "$RECENT_LOGS" | while read line; do
        if echo "$line" | grep -q "forwardedHost: mcp-bridge.local"; then
            echo "  ✅ $line"
        elif echo "$line" | grep -q "forwardedHost: null"; then
            echo "  ⚠️  $line (Nginx 可能未传递头)"
        else
            echo "  ℹ️  $line"
        fi
    done
else
    echo "⚠️  没有找到相关日志"
fi

echo ""

# 6. 测试 tools/list
echo "6. 测试 tools/list 请求..."
if [ -n "$SESSION_ID" ]; then
    TOOLS_RESPONSE=$(curl -s -X POST "http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=$SESSION_ID" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":1}')
    
    if echo "$TOOLS_RESPONSE" | grep -q "tools"; then
        echo "✅ tools/list 请求成功"
        TOOL_COUNT=$(echo "$TOOLS_RESPONSE" | grep -o '"name"' | wc -l | tr -d ' ')
        echo "   找到 $TOOL_COUNT 个工具"
    else
        echo "❌ tools/list 请求失败"
        echo "响应: $TOOLS_RESPONSE"
    fi
else
    echo "⚠️  跳过 tools/list 测试（没有 sessionId）"
fi

echo ""

# 总结
echo "=== 验证总结 ==="
echo ""
echo "如果所有测试都通过 ✅，说明修复成功！"
echo "如果有 ❌ 或 ⚠️，请检查："
echo "  1. Nginx 配置是否已重载：sudo nginx -s reload"
echo "  2. 应用是否正常运行"
echo "  3. 查看详细日志：tail -f logs/router-8051.log"




