#!/bin/bash

echo "=== 诊断 SSE 连接断开问题 ==="
echo ""

URL="${1:-http://mcp-bridge.test/sse/mcp-server-v6}"
DURATION="${2:-60}"

echo "测试 URL: ${URL}"
echo "测试时长: ${DURATION} 秒"
echo ""

# 1. 检查 Nginx 配置
echo "1. 检查 Nginx 超时配置..."
if [ -f "nginx/nginx.conf" ]; then
    echo "   proxy_read_timeout: $(grep "proxy_read_timeout" nginx/nginx.conf | grep -v "^#" | awk '{print $2}')"
    echo "   proxy_send_timeout: $(grep "proxy_send_timeout" nginx/nginx.conf | grep -v "^#" | awk '{print $2}')"
    echo "   proxy_connect_timeout: $(grep "proxy_connect_timeout" nginx/nginx.conf | grep -v "^#" | awk '{print $2}')"
else
    echo "   ⚠️  Nginx 配置文件不存在"
fi
echo ""

# 2. 检查 Nginx 状态
echo "2. 检查 Nginx 状态..."
if pgrep -x nginx > /dev/null; then
    echo "   ✅ Nginx 正在运行"
    NGINX_PID=$(pgrep -x nginx | head -1)
    echo "   PID: ${NGINX_PID}"
else
    echo "   ❌ Nginx 未运行"
fi
echo ""

# 3. 测试 SSE 连接
echo "3. 测试 SSE 连接（${DURATION} 秒）..."
echo "   使用 curl 建立 SSE 连接..."
echo ""

START_TIME=$(date +%s)
CONNECTION_COUNT=0
DISCONNECT_COUNT=0

# 使用 timeout 限制测试时间
timeout ${DURATION} curl -N -H "Accept: text/event-stream" \
    -H "Cache-Control: no-cache" \
    --max-time ${DURATION} \
    "${URL}" 2>&1 | while IFS= read -r line; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [[ "$line" =~ ^data: ]]; then
        CONNECTION_COUNT=$((CONNECTION_COUNT + 1))
        echo "[${ELAPSED}s] 收到数据: ${line:0:100}..."
    elif [[ "$line" =~ ^event: ]]; then
        echo "[${ELAPSED}s] 事件: ${line}"
    elif [[ "$line" =~ ^id: ]]; then
        echo "[${ELAPSED}s] ID: ${line}"
    elif [[ "$line" =~ "curl:" ]] || [[ "$line" =~ "error" ]] || [[ "$line" =~ "timeout" ]]; then
        DISCONNECT_COUNT=$((DISCONNECT_COUNT + 1))
        echo "[${ELAPSED}s] ⚠️  连接问题: ${line}"
    fi
done

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))

echo ""
echo "   测试完成："
echo "   - 总时长: ${TOTAL_TIME} 秒"
echo "   - 预期时长: ${DURATION} 秒"
if [ ${TOTAL_TIME} -lt ${DURATION} ]; then
    echo "   ⚠️  连接提前断开（提前 ${DURATION} - ${TOTAL_TIME} = $((DURATION - TOTAL_TIME)) 秒）"
fi
echo ""

# 4. 检查应用日志
echo "4. 检查应用日志（最近 20 行 SSE 相关）..."
if [ -d "logs" ]; then
    tail -100 logs/*.log 2>/dev/null | grep -i "sse\|disconnect\|cancel\|error" | tail -20 || echo "   无相关日志"
else
    echo "   ⚠️  日志目录不存在"
fi
echo ""

# 5. 检查负载均衡
echo "5. 检查负载均衡配置..."
if [ -f "nginx/nginx.conf" ]; then
    echo "   负载均衡策略: $(grep -A 2 "upstream" nginx/nginx.conf | grep -v "^#" | head -1)"
    echo "   后端服务器:"
    grep "server 127.0.0.1" nginx/nginx.conf | grep -v "^#" | sed 's/^/      /'
fi
echo ""

# 6. 建议
echo "=== 可能的原因和建议 ==="
echo ""
echo "1. Nginx 超时设置过短"
echo "   检查: proxy_read_timeout（SSE 长连接需要足够长）"
echo "   建议: 至少 600s (10分钟) 或更长"
echo ""
echo "2. 负载均衡问题"
echo "   如果使用 ip_hash，确保客户端 IP 稳定"
echo "   如果使用轮询，SSE 连接可能被路由到不同实例"
echo ""
echo "3. 心跳机制问题"
echo "   检查应用日志中的心跳消息"
echo "   心跳间隔应该是 30 秒"
echo ""
echo "4. 客户端主动断开"
echo "   检查客户端代码是否有超时或重连逻辑"
echo ""
