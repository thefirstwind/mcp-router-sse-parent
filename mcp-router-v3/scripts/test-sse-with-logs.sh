#!/bin/bash
# 测试 SSE 连接并实时查看日志

echo "=== 测试 SSE 连接 ==="
echo ""

# 清空之前的日志标记
echo "开始测试..." > /tmp/sse-test-start.txt

# 在后台启动 curl 测试
echo "启动 SSE 连接测试..."
timeout 5 curl -N http://mcp-bridge.local/sse/mcp-server-v6 2>&1 | head -5 &
CURL_PID=$!

# 等待一下让连接建立
sleep 2

# 查看应用日志
echo ""
echo "=== 应用日志（SSE 相关）==="
tail -50 logs/router-8051.log 2>&1 | grep -E "(SSE connection|Building base URL|Host=|forwardedHost|endpoint|subscribed|cancelled)" | tail -10

# 等待 curl 完成
wait $CURL_PID 2>/dev/null

echo ""
echo "=== 测试完成 ==="
echo ""
echo "如果看到 X-Forwarded-Host=null，说明 Nginx 没有传递头"
echo "如果看到 X-Forwarded-Host=mcp-bridge.local，说明 Nginx 配置正常"




