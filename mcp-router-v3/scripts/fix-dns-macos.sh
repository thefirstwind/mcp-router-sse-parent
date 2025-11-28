#!/bin/bash

echo "=== 修复 macOS DNS 解析慢问题 ==="
echo ""

DOMAIN="mcp-bridge.local"

# 方法 1: 刷新 DNS 缓存
echo "1. 刷新 DNS 缓存..."
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder
echo "   ✅ 完成"
echo ""

# 方法 2: 禁用 mDNS 对 .local 的优先处理（临时）
echo "2. 检查 mDNSResponder 配置..."
if [ -f /etc/resolv.conf ]; then
    echo "   /etc/resolv.conf 内容："
    cat /etc/resolv.conf
else
    echo "   ⚠️  /etc/resolv.conf 不存在"
fi
echo ""

# 方法 3: 使用 dnsmasq（推荐）
echo "3. 检查是否可以使用 dnsmasq..."
if brew list dnsmasq &> /dev/null 2>&1; then
    echo "   ✅ dnsmasq 已安装"
    if brew services list | grep -q "dnsmasq.*started"; then
        echo "   ✅ dnsmasq 正在运行"
    else
        echo "   ⚠️  dnsmasq 未运行，运行: brew services start dnsmasq"
    fi
else
    echo "   ⚠️  dnsmasq 未安装"
    echo "   安装: brew install dnsmasq"
    echo "   配置: ./scripts/setup-dnsmasq.sh"
fi
echo ""

# 方法 4: 测试使用 scutil 查询
echo "4. 使用 scutil 查询 DNS 配置..."
scutil --dns | grep -A 5 "resolver #" | head -20
echo ""

# 方法 5: 强制使用 /etc/hosts（通过修改系统 DNS 顺序）
echo "5. 检查系统 DNS 配置..."
CURRENT_DNS=$(networksetup -getdnsservers Wi-Fi 2>/dev/null | head -1)
if [ -n "$CURRENT_DNS" ]; then
    echo "   当前 Wi-Fi DNS: ${CURRENT_DNS}"
    if [ "$CURRENT_DNS" = "127.0.0.1" ]; then
        echo "   ✅ 已配置为使用本地 DNS (dnsmasq)"
    else
        echo "   ⚠️  建议配置为 127.0.0.1 (使用 dnsmasq)"
        echo "   命令: sudo networksetup -setdnsservers Wi-Fi 127.0.0.1"
    fi
else
    echo "   ⚠️  无法获取 DNS 配置"
fi
echo ""

# 方法 6: 测试解析
echo "6. 测试 DNS 解析..."
echo "   使用 dig:"
time dig +short ${DOMAIN} 2>&1 | head -1
echo ""

echo "   使用 ping:"
timeout 2 ping -c 1 ${DOMAIN} 2>&1 | grep -E "(time=|timeout|unknown host)" || echo "   超时或失败"
echo ""

echo "=== 建议 ==="
echo ""
echo "macOS 对 .local 域名的处理有特殊机制："
echo "1. 优先使用 mDNS (Bonjour)"
echo "2. 如果 mDNS 查询失败（5秒超时），才回退到 /etc/hosts"
echo ""
echo "解决方案："
echo "1. 使用 dnsmasq（推荐）："
echo "   ./scripts/setup-dnsmasq.sh"
echo ""
echo "2. 或者使用真实域名（生产环境）："
echo "   不使用 .local，改用 .test 或其他域名"
echo ""
echo "3. 或者修改系统 DNS 顺序（临时）："
echo "   sudo networksetup -setdnsservers Wi-Fi 127.0.0.1"
echo "   （需要先安装和配置 dnsmasq）"
