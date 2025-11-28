#!/bin/bash

echo "=== 验证所有客户端 DNS 解析 ==="
echo ""

DOMAIN="mcp-bridge.local"
IP="127.0.0.1"

# 1. 测试 ping
echo "1. 测试 ping："
if ping -c 1 -W 1000 ${DOMAIN} > /dev/null 2>&1; then
    PING_TIME=$(ping -c 1 -W 1000 ${DOMAIN} 2>&1 | grep "time=" | awk '{print $7}' | cut -d= -f2)
    if [ -n "$PING_TIME" ]; then
        echo "   ✅ ping 成功，响应时间: ${PING_TIME}"
    else
        echo "   ✅ ping 成功"
    fi
else
    echo "   ❌ ping 失败（可能超时）"
fi
echo ""

# 2. 测试 curl（不使用 --resolve）
echo "2. 测试 curl（不使用 --resolve）："
START_TIME=$(date +%s.%N)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://${DOMAIN}/actuator/health" 2>/dev/null)
END_TIME=$(date +%s.%N)
DURATION=$(echo "$END_TIME - $START_TIME" | bc -l)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
    if (( $(echo "$DURATION < 1" | bc -l) )); then
        echo "   ✅ curl 成功，响应时间: ${DURATION} 秒（HTTP ${HTTP_CODE}）"
    else
        echo "   ⚠️  curl 成功但较慢，响应时间: ${DURATION} 秒（HTTP ${HTTP_CODE}）"
    fi
else
    echo "   ❌ curl 失败或超时（HTTP ${HTTP_CODE}，耗时 ${DURATION} 秒）"
fi
echo ""

# 3. 测试 getent（如果可用）
echo "3. 测试 getent（系统 DNS 解析）："
if command -v getent &> /dev/null; then
    RESULT=$(getent hosts ${DOMAIN} 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "   ✅ getent 成功: ${RESULT}"
    else
        echo "   ❌ getent 失败"
    fi
else
    echo "   ⚠️  getent 不可用（跳过）"
fi
echo ""

# 4. 检查 /etc/hosts
echo "4. 检查 /etc/hosts："
if grep -q "${DOMAIN}" /etc/hosts 2>/dev/null; then
    ENTRY=$(grep "${DOMAIN}" /etc/hosts)
    echo "   ✅ 已配置: ${ENTRY}"
else
    echo "   ❌ 未配置"
fi
echo ""

# 5. 检查 dnsmasq（如果安装）
echo "5. 检查 dnsmasq："
if brew list dnsmasq &> /dev/null 2>&1; then
    if brew services list | grep -q "dnsmasq.*started"; then
        echo "   ✅ dnsmasq 已安装并运行"
    else
        echo "   ⚠️  dnsmasq 已安装但未运行"
    fi
else
    echo "   ⚠️  dnsmasq 未安装"
fi
echo ""

# 6. 总结
echo "=== 总结 ==="
if grep -q "${DOMAIN}" /etc/hosts 2>/dev/null; then
    echo "✅ /etc/hosts 已配置"
    echo ""
    echo "如果仍然慢，请："
    echo "1. 刷新 DNS 缓存: sudo dscacheutil -flushcache && sudo killall -HUP mDNSResponder"
    echo "2. 或配置 dnsmasq: ./scripts/setup-dnsmasq.sh"
else
    echo "⚠️  /etc/hosts 未配置"
    echo ""
    echo "请运行: ./scripts/setup-local-dns.sh"
fi
