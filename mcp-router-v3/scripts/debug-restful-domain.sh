#!/bin/bash

echo "=== RESTful 接口域名访问性能排查 ==="
echo ""

DOMAIN="mcp-bridge.local"
PORT=80
ENDPOINT="/mcp/router/tools/mcp-server-v6"
FULL_URL="http://${DOMAIN}:${PORT}${ENDPOINT}"

echo "测试目标: ${FULL_URL}"
echo ""

# 1. DNS 解析时间
echo "1. DNS 解析时间测试"
echo "-------------------"
time_namelookup=$(curl -o /dev/null -s -w "%{time_namelookup}\n" "${FULL_URL}" 2>&1 | tail -1)
echo "DNS 解析时间: ${time_namelookup} 秒"
if (( $(echo "${time_namelookup} > 0.1" | bc -l) )); then
    echo "⚠️  DNS 解析较慢（> 0.1秒）"
fi
echo ""

# 2. 连接建立时间
echo "2. 连接建立时间测试"
echo "-------------------"
time_connect=$(curl -o /dev/null -s -w "%{time_connect}\n" "${FULL_URL}" 2>&1 | tail -1)
echo "连接建立时间: ${time_connect} 秒"
if (( $(echo "${time_connect} > 0.5" | bc -l) )); then
    echo "⚠️  连接建立较慢（> 0.5秒）"
fi
echo ""

# 3. 完整请求时间（包括 DNS、连接、传输）
echo "3. 完整请求时间测试"
echo "-------------------"
time_total=$(curl -o /dev/null -s -w "%{time_total}\n" "${FULL_URL}" 2>&1 | tail -1)
echo "总时间: ${time_total} 秒"
echo ""

# 4. 详细时间分解
echo "4. 详细时间分解"
echo "-------------------"
curl -o /dev/null -s -w "DNS 解析: %{time_namelookup}s\n连接建立: %{time_connect}s\n开始传输: %{time_starttransfer}s\n总时间: %{time_total}s\n" "${FULL_URL}"
echo ""

# 5. 使用 IP 地址对比测试
echo "5. 使用 IP 地址对比测试"
echo "-------------------"
IP="127.0.0.1"
IP_URL="http://${IP}:${PORT}${ENDPOINT}"
echo "测试: ${IP_URL}"
time_ip=$(curl -o /dev/null -s -w "%{time_total}\n" "${IP_URL}" 2>&1 | tail -1)
echo "IP 访问总时间: ${time_ip} 秒"
echo ""

# 6. 对比分析
echo "6. 对比分析"
echo "-------------------"
if (( $(echo "${time_total} > ${time_ip}" | bc -l) )); then
    diff=$(echo "${time_total} - ${time_ip}" | bc -l)
    echo "域名访问比 IP 访问慢: ${diff} 秒"
    if (( $(echo "${diff} > 1" | bc -l) )); then
        echo "⚠️  域名访问明显慢于 IP 访问（> 1秒）"
    fi
else
    echo "✅ 域名访问和 IP 访问时间相近"
fi
echo ""

# 7. 检查 Nginx 状态
echo "7. Nginx 状态检查"
echo "-------------------"
if pgrep -x nginx > /dev/null; then
    echo "✅ Nginx 正在运行"
    echo "Nginx 进程:"
    ps aux | grep nginx | grep -v grep | head -3
else
    echo "❌ Nginx 未运行"
fi
echo ""

# 8. 检查后端服务状态
echo "8. 后端服务状态检查"
echo "-------------------"
for port in 8051 8052 8053; do
    if curl -s "http://127.0.0.1:${port}/actuator/health" > /dev/null 2>&1; then
        echo "✅ 实例 ${port} 健康"
    else
        echo "❌ 实例 ${port} 不可用"
    fi
done
echo ""

# 9. 测试直接访问后端（绕过 Nginx）
echo "9. 直接访问后端测试（绕过 Nginx）"
echo "-------------------"
for port in 8051 8052 8053; do
    DIRECT_URL="http://127.0.0.1:${port}${ENDPOINT}"
    time_direct=$(curl -o /dev/null -s -w "%{time_total}\n" "${DIRECT_URL}" 2>&1 | tail -1)
    echo "实例 ${port} 直接访问: ${time_direct} 秒"
done
echo ""

echo "=== 排查完成 ==="
