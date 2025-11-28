#!/bin/bash

echo "=== 详细诊断域名访问慢问题 ==="
echo ""

DOMAIN="mcp-bridge.local"
URL="http://${DOMAIN}/mcp/router/tools/mcp-server-v6"

# 1. DNS 解析测试（详细）
echo "1. DNS 解析测试："
echo "   - 使用 dig："
START=$(date +%s.%N)
DIG_RESULT=$(dig +short ${DOMAIN} 2>&1)
END=$(date +%s.%N)
DIG_TIME=$(echo "$END - $START" | bc -l)
echo "     结果: ${DIG_RESULT}"
echo "     耗时: ${DIG_TIME} 秒"
echo ""

echo "   - 使用 nslookup："
START=$(date +%s.%N)
NSLOOKUP_RESULT=$(nslookup ${DOMAIN} 2>&1 | grep -A 1 "Name:" | tail -1)
END=$(date +%s.%N)
NSLOOKUP_TIME=$(echo "$END - $START" | bc -l)
echo "     结果: ${NSLOOKUP_RESULT}"
echo "     耗时: ${NSLOOKUP_TIME} 秒"
echo ""

echo "   - 使用 getent（如果可用）："
if command -v getent &> /dev/null; then
    START=$(date +%s.%N)
    GETENT_RESULT=$(getent hosts ${DOMAIN} 2>&1)
    END=$(date +%s.%N)
    GETENT_TIME=$(echo "$END - $START" | bc -l)
    echo "     结果: ${GETENT_RESULT}"
    echo "     耗时: ${GETENT_TIME} 秒"
else
    echo "     getent 不可用"
fi
echo ""

# 2. 检查 /etc/hosts
echo "2. 检查 /etc/hosts："
HOSTS_ENTRY=$(grep "${DOMAIN}" /etc/hosts 2>/dev/null)
if [ -n "$HOSTS_ENTRY" ]; then
    echo "   ✅ 已配置: ${HOSTS_ENTRY}"
else
    echo "   ❌ 未配置"
fi
echo ""

# 3. 测试 TCP 连接（不发送 HTTP 请求）
echo "3. 测试 TCP 连接（端口 80）："
START=$(date +%s.%N)
if timeout 2 bash -c "echo > /dev/tcp/${DOMAIN}/80" 2>/dev/null; then
    END=$(date +%s.%N)
    TCP_TIME=$(echo "$END - $START" | bc -l)
    echo "   ✅ TCP 连接成功，耗时: ${TCP_TIME} 秒"
else
    END=$(date +%s.%N)
    TCP_TIME=$(echo "$END - $START" | bc -l)
    echo "   ❌ TCP 连接失败或超时，耗时: ${TCP_TIME} 秒"
fi
echo ""

# 4. 测试 HTTP 连接（使用 curl，详细输出）
echo "4. 测试 HTTP 连接（curl，详细输出）："
echo "   执行: curl -v --max-time 5 ${URL}"
echo ""
START=$(date +%s.%N)
HTTP_OUTPUT=$(curl -v --max-time 5 -w "\nHTTP_CODE:%{http_code}\nTIME_NAMELOOKUP:%{time_namelookup}\nTIME_CONNECT:%{time_connect}\nTIME_STARTTRANSFER:%{time_starttransfer}\nTIME_TOTAL:%{time_total}\n" "${URL}" 2>&1)
END=$(date +%s.%N)
TOTAL_TIME=$(echo "$END - $START" | bc -l)

echo "${HTTP_OUTPUT}" | head -20
echo ""
echo "   总耗时: ${TOTAL_TIME} 秒"
echo ""

# 提取 curl 详细时间
NAMELOOKUP=$(echo "${HTTP_OUTPUT}" | grep "TIME_NAMELOOKUP:" | cut -d: -f2)
CONNECT=$(echo "${HTTP_OUTPUT}" | grep "TIME_CONNECT:" | cut -d: -f2)
STARTTRANSFER=$(echo "${HTTP_OUTPUT}" | grep "TIME_STARTTRANSFER:" | cut -d: -f2)
TOTAL_CURL=$(echo "${HTTP_OUTPUT}" | grep "TIME_TOTAL:" | cut -d: -f2)
HTTP_CODE=$(echo "${HTTP_OUTPUT}" | grep "HTTP_CODE:" | cut -d: -f2)

if [ -n "$NAMELOOKUP" ]; then
    echo "   DNS 解析时间: ${NAMELOOKUP} 秒"
fi
if [ -n "$CONNECT" ]; then
    echo "   TCP 连接时间: ${CONNECT} 秒"
fi
if [ -n "$STARTTRANSFER" ]; then
    echo "   首字节时间: ${STARTTRANSFER} 秒"
fi
if [ -n "$TOTAL_CURL" ]; then
    echo "   总时间: ${TOTAL_CURL} 秒"
fi
if [ -n "$HTTP_CODE" ]; then
    echo "   HTTP 状态码: ${HTTP_CODE}"
fi
echo ""

# 5. 对比：直接访问 IP
echo "5. 对比：直接访问 IP (127.0.0.1:80)："
IP_URL="http://127.0.0.1/mcp/router/tools/mcp-server-v6"
START=$(date +%s.%N)
IP_HTTP_OUTPUT=$(curl -s -w "\nHTTP_CODE:%{http_code}\nTIME_TOTAL:%{time_total}\n" --max-time 5 "${IP_URL}" 2>&1 | tail -3)
END=$(date +%s.%N)
IP_TOTAL_TIME=$(echo "$END - $START" | bc -l)

IP_HTTP_CODE=$(echo "${IP_HTTP_OUTPUT}" | grep "HTTP_CODE:" | cut -d: -f2)
IP_TIME_TOTAL=$(echo "${IP_HTTP_OUTPUT}" | grep "TIME_TOTAL:" | cut -d: -f2)

if [ -n "$IP_HTTP_CODE" ]; then
    echo "   HTTP 状态码: ${IP_HTTP_CODE}"
fi
if [ -n "$IP_TIME_TOTAL" ]; then
    echo "   总时间: ${IP_TIME_TOTAL} 秒"
fi
echo "   总耗时: ${IP_TOTAL_TIME} 秒"
echo ""

# 6. 检查 Nginx 状态
echo "6. 检查 Nginx 状态："
if pgrep -x nginx > /dev/null; then
    echo "   ✅ Nginx 正在运行"
    NGINX_PID=$(pgrep -x nginx | head -1)
    echo "   PID: ${NGINX_PID}"
else
    echo "   ❌ Nginx 未运行"
fi
echo ""

# 7. 检查后端服务
echo "7. 检查后端服务（直接访问 8051/8052/8053）："
for PORT in 8051 8052 8053; do
    START=$(date +%s.%N)
    BACKEND_URL="http://127.0.0.1:${PORT}/mcp/router/tools/mcp-server-v6"
    BACKEND_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "${BACKEND_URL}" 2>/dev/null)
    END=$(date +%s.%N)
    BACKEND_TIME=$(echo "$END - $START" | bc -l)
    if [ "$BACKEND_CODE" = "200" ] || [ "$BACKEND_CODE" = "404" ]; then
        echo "   ✅ 端口 ${PORT}: HTTP ${BACKEND_CODE}, 耗时 ${BACKEND_TIME} 秒"
    else
        echo "   ⚠️  端口 ${PORT}: HTTP ${BACKEND_CODE}, 耗时 ${BACKEND_TIME} 秒"
    fi
done
echo ""

# 8. 检查 DNS 缓存
echo "8. 检查 DNS 缓存状态："
if command -v dscacheutil &> /dev/null; then
    echo "   使用 dscacheutil 查询缓存："
    dscacheutil -q host -a name ${DOMAIN} 2>&1 | head -5
fi
echo ""

# 9. 总结和建议
echo "=== 诊断总结 ==="
echo ""

if [ -n "$NAMELOOKUP" ] && (( $(echo "$NAMELOOKUP > 1" | bc -l) )); then
    echo "⚠️  DNS 解析时间过长: ${NAMELOOKUP} 秒"
    echo "   建议："
    echo "   1. 刷新 DNS 缓存: sudo dscacheutil -flushcache && sudo killall -HUP mDNSResponder"
    echo "   2. 配置 dnsmasq: ./scripts/setup-dnsmasq.sh"
    echo ""
fi

if [ -n "$TOTAL_CURL" ] && (( $(echo "$TOTAL_CURL > 1" | bc -l) )); then
    echo "⚠️  总响应时间过长: ${TOTAL_CURL} 秒"
    if [ -n "$NAMELOOKUP" ] && (( $(echo "$NAMELOOKUP > 0.5" | bc -l) )); then
        echo "   主要原因：DNS 解析慢"
    elif [ -n "$CONNECT" ] && (( $(echo "$CONNECT > 0.5" | bc -l) )); then
        echo "   主要原因：TCP 连接慢"
    elif [ -n "$STARTTRANSFER" ] && (( $(echo "$STARTTRANSFER > 0.5" | bc -l) )); then
        echo "   主要原因：服务器响应慢"
    fi
    echo ""
fi

if [ -n "$IP_TIME_TOTAL" ] && [ -n "$TOTAL_CURL" ]; then
    DIFF=$(echo "$TOTAL_CURL - $IP_TIME_TOTAL" | bc -l)
    if (( $(echo "$DIFF > 1" | bc -l) )); then
        echo "⚠️  域名访问比 IP 访问慢 ${DIFF} 秒"
        echo "   这确认了 DNS 解析是瓶颈"
        echo ""
    fi
fi

echo "=== 诊断完成 ==="
