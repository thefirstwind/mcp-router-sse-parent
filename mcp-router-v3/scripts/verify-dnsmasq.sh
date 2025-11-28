#!/bin/bash

echo "=== 验证 dnsmasq 配置 ==="
echo ""

DOMAIN="mcp-bridge.local"

# 1. 检查 dnsmasq 状态
echo "1. 检查 dnsmasq 状态..."
if brew services list | grep -q "dnsmasq.*started"; then
    echo "   ✅ dnsmasq 正在运行"
else
    echo "   ❌ dnsmasq 未运行"
    echo "   请执行: sudo brew services start dnsmasq"
    exit 1
fi
echo ""

# 2. 测试 dnsmasq 直接查询
echo "2. 测试 dnsmasq 直接查询..."
RESULT=$(dig @127.0.0.1 ${DOMAIN} +short 2>/dev/null | head -1)
if [ "$RESULT" = "127.0.0.1" ]; then
    echo "   ✅ dnsmasq 正常工作，返回: ${RESULT}"
else
    echo "   ❌ dnsmasq 未正确配置，返回: ${RESULT}"
    echo "   请检查配置文件: /opt/homebrew/etc/dnsmasq.conf"
    exit 1
fi
echo ""

# 3. 检查系统 DNS 配置
echo "3. 检查系统 DNS 配置..."
DNS_WIFI=$(networksetup -getdnsservers Wi-Fi 2>/dev/null | head -1 || echo "")
DNS_ETHERNET=$(networksetup -getdnsservers Ethernet 2>/dev/null | head -1 || echo "")

if [ "$DNS_WIFI" = "127.0.0.1" ] || [ "$DNS_ETHERNET" = "127.0.0.1" ]; then
    echo "   ✅ 系统 DNS 已配置为使用 dnsmasq"
    if [ "$DNS_WIFI" = "127.0.0.1" ]; then
        echo "      Wi-Fi: 127.0.0.1"
    fi
    if [ "$DNS_ETHERNET" = "127.0.0.1" ]; then
        echo "      Ethernet: 127.0.0.1"
    fi
else
    echo "   ⚠️  系统 DNS 未配置为使用 dnsmasq"
    echo "   当前 Wi-Fi DNS: ${DNS_WIFI:-未配置}"
    echo "   当前 Ethernet DNS: ${DNS_ETHERNET:-未配置}"
    echo ""
    echo "   请配置系统 DNS 为 127.0.0.1（见 docs/DNSMASQ_SETUP_GUIDE.md）"
    echo ""
fi
echo ""

# 4. 测试 DNS 解析速度
echo "4. 测试 DNS 解析速度..."
START=$(date +%s.%N)
RESULT=$(dig +short ${DOMAIN} 2>&1 | head -1)
END=$(date +%s.%N)
TIME=$(echo "$END - $START" | bc -l)

if [ -n "$RESULT" ] && [ "$RESULT" = "127.0.0.1" ]; then
    if (( $(echo "$TIME < 0.1" | bc -l) )); then
        echo "   ✅ DNS 解析成功，耗时: ${TIME} 秒（正常）"
    else
        echo "   ⚠️  DNS 解析成功，但耗时: ${TIME} 秒（可能仍在使用 mDNS）"
        echo "      请确认系统 DNS 已配置为 127.0.0.1"
    fi
else
    echo "   ❌ DNS 解析失败或超时"
    echo "      结果: ${RESULT}"
fi
echo ""

# 5. 测试 HTTP 请求
echo "5. 测试 HTTP 请求..."
START=$(date +%s.%N)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://${DOMAIN}/mcp/router/tools/mcp-server-v6" 2>/dev/null)
END=$(date +%s.%N)
HTTP_TIME=$(echo "$END - $START" | bc -l)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
    if (( $(echo "$HTTP_TIME < 1" | bc -l) )); then
        echo "   ✅ HTTP 请求成功，耗时: ${HTTP_TIME} 秒（HTTP ${HTTP_CODE}）"
    else
        echo "   ⚠️  HTTP 请求成功但较慢，耗时: ${HTTP_TIME} 秒（HTTP ${HTTP_CODE}）"
    fi
else
    echo "   ❌ HTTP 请求失败或超时（HTTP ${HTTP_CODE}，耗时 ${HTTP_TIME} 秒）"
fi
echo ""

# 6. 总结
echo "=== 验证总结 ==="
if [ "$DNS_WIFI" = "127.0.0.1" ] || [ "$DNS_ETHERNET" = "127.0.0.1" ]; then
    if (( $(echo "$TIME < 0.1" | bc -l) )); then
        echo "✅ dnsmasq 配置成功！DNS 解析速度正常"
    else
        echo "⚠️  dnsmasq 已配置，但 DNS 解析仍然慢"
        echo "   请刷新 DNS 缓存: sudo dscacheutil -flushcache && sudo killall -HUP mDNSResponder"
    fi
else
    echo "⚠️  请完成系统 DNS 配置（见 docs/DNSMASQ_SETUP_GUIDE.md）"
fi
echo ""
