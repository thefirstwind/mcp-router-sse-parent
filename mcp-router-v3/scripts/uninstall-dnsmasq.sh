#!/bin/bash

echo "=== 卸载 dnsmasq ==="
echo ""

# 1. 停止 dnsmasq 服务
echo "1. 停止 dnsmasq 服务..."
if brew services list | grep -q "dnsmasq.*started"; then
    echo "   正在停止 dnsmasq..."
    sudo brew services stop dnsmasq
    sleep 2
    if brew services list | grep -q "dnsmasq.*started"; then
        echo "   ⚠️  dnsmasq 仍在运行，尝试强制停止..."
        sudo pkill dnsmasq
    else
        echo "   ✅ dnsmasq 已停止"
    fi
else
    echo "   ✅ dnsmasq 未运行"
fi
echo ""

# 2. 检查是否有手动启动的进程
echo "2. 检查是否有 dnsmasq 进程..."
DNSMASQ_PIDS=$(ps aux | grep "[d]nsmasq" | awk '{print $2}')
if [ -n "$DNSMASQ_PIDS" ]; then
    echo "   发现 dnsmasq 进程: $DNSMASQ_PIDS"
    echo "   正在停止..."
    sudo pkill dnsmasq
    sleep 1
    echo "   ✅ 进程已停止"
else
    echo "   ✅ 没有运行中的 dnsmasq 进程"
fi
echo ""

# 3. 卸载 dnsmasq
echo "3. 卸载 dnsmasq..."
if brew list dnsmasq &> /dev/null; then
    echo "   正在卸载 dnsmasq..."
    brew uninstall dnsmasq
    if [ $? -eq 0 ]; then
        echo "   ✅ dnsmasq 已卸载"
    else
        echo "   ❌ 卸载失败"
    fi
else
    echo "   ✅ dnsmasq 未安装"
fi
echo ""

# 4. 恢复系统 DNS 设置
echo "4. 恢复系统 DNS 设置..."
CURRENT_DNS_WIFI=$(networksetup -getdnsservers Wi-Fi 2>/dev/null | head -1 || echo "")
CURRENT_DNS_ETHERNET=$(networksetup -getdnsservers Ethernet 2>/dev/null | head -1 || echo "")

if [ "$CURRENT_DNS_WIFI" = "127.0.0.1" ]; then
    echo "   Wi-Fi DNS 设置为 127.0.0.1，需要恢复"
    read -p "   是否恢复 Wi-Fi DNS 设置？(y/n): " restore_wifi
    if [ "$restore_wifi" = "y" ] || [ "$restore_wifi" = "Y" ]; then
        sudo networksetup -setdnsservers Wi-Fi "Empty"
        echo "   ✅ Wi-Fi DNS 已恢复为系统默认"
    fi
fi

if [ "$CURRENT_DNS_ETHERNET" = "127.0.0.1" ]; then
    echo "   Ethernet DNS 设置为 127.0.0.1，需要恢复"
    read -p "   是否恢复 Ethernet DNS 设置？(y/n): " restore_ethernet
    if [ "$restore_ethernet" = "y" ] || [ "$restore_ethernet" = "Y" ]; then
        sudo networksetup -setdnsservers Ethernet "Empty"
        echo "   ✅ Ethernet DNS 已恢复为系统默认"
    fi
fi

if [ "$CURRENT_DNS_WIFI" != "127.0.0.1" ] && [ "$CURRENT_DNS_ETHERNET" != "127.0.0.1" ]; then
    echo "   ✅ 系统 DNS 未配置为 127.0.0.1，无需恢复"
fi
echo ""

# 5. 清理配置文件（可选）
echo "5. 清理配置文件..."
CONFIG_FILE="/opt/homebrew/etc/dnsmasq.conf"
if [ -f "${CONFIG_FILE}" ]; then
    echo "   配置文件存在: ${CONFIG_FILE}"
    read -p "   是否删除配置文件？(y/n): " delete_config
    if [ "$delete_config" = "y" ] || [ "$delete_config" = "Y" ]; then
        rm -f "${CONFIG_FILE}"
        echo "   ✅ 配置文件已删除"
    else
        echo "   ⚠️  配置文件保留: ${CONFIG_FILE}"
    fi
else
    echo "   ✅ 配置文件不存在"
fi
echo ""

# 6. 刷新 DNS 缓存
echo "6. 刷新 DNS 缓存..."
sudo dscacheutil -flushcache 2>/dev/null || true
sudo killall -HUP mDNSResponder 2>/dev/null || true
echo "   ✅ DNS 缓存已刷新"
echo ""

# 7. 验证卸载
echo "7. 验证卸载..."
if brew list dnsmasq &> /dev/null; then
    echo "   ❌ dnsmasq 仍然安装"
else
    echo "   ✅ dnsmasq 已完全卸载"
fi

if ps aux | grep -q "[d]nsmasq"; then
    echo "   ⚠️  仍有 dnsmasq 进程运行"
else
    echo "   ✅ 没有运行中的 dnsmasq 进程"
fi
echo ""

echo "=== 卸载完成 ==="
echo ""
echo "注意："
echo "1. 如果之前使用 dnsmasq 解析 .local 域名，现在将回退到系统默认行为"
echo "2. 如果需要快速访问，可以使用 /etc/hosts 或改用 .test 域名"
echo "3. 系统 DNS 设置已恢复（如果选择了恢复）"
echo ""
