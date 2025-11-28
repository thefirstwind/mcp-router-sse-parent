#!/bin/bash

echo "=== 配置 dnsmasq（推荐，所有应用受益）==="
echo ""

# 检查是否已安装
if ! command -v brew &> /dev/null; then
    echo "❌ 未找到 Homebrew，请先安装 Homebrew"
    echo "   访问: https://brew.sh"
    exit 1
fi

# 检查是否已安装 dnsmasq
if ! brew list dnsmasq &> /dev/null; then
    echo "安装 dnsmasq..."
    brew install dnsmasq
    if [ $? -ne 0 ]; then
        echo "❌ 安装失败"
        exit 1
    fi
    echo "✅ dnsmasq 已安装"
else
    echo "✅ dnsmasq 已安装"
fi

# 配置 dnsmasq
CONFIG_FILE="/usr/local/etc/dnsmasq.conf"
if grep -q "address=/.local/127.0.0.1" "${CONFIG_FILE}" 2>/dev/null; then
    echo "✅ dnsmasq 配置已存在"
else
    echo "配置 dnsmasq..."
    echo 'address=/.local/127.0.0.1' >> "${CONFIG_FILE}"
    echo "✅ 配置已添加"
fi

# 启动 dnsmasq
echo "启动 dnsmasq..."
brew services start dnsmasq
if [ $? -eq 0 ]; then
    echo "✅ dnsmasq 已启动"
else
    echo "⚠️  启动失败，请检查配置"
fi

echo ""
echo "=== 下一步：配置系统 DNS ==="
echo ""
echo "请按以下步骤配置系统 DNS："
echo ""
echo "1. 打开：系统偏好设置 > 网络"
echo "2. 选择当前网络（Wi-Fi 或 Ethernet）"
echo "3. 点击'高级' > 'DNS'"
echo "4. 点击左下角的 '+' 添加 DNS 服务器"
echo "5. 输入: 127.0.0.1"
echo "6. 将 127.0.0.1 拖到列表最前面（最重要）"
echo "7. 点击'好'保存"
echo ""
echo "或者使用命令行（需要管理员权限）："
echo "sudo networksetup -setdnsservers Wi-Fi 127.0.0.1"
echo "sudo networksetup -setdnsservers Ethernet 127.0.0.1"
echo ""
echo "配置后，刷新 DNS 缓存："
echo "sudo dscacheutil -flushcache"
echo "sudo killall -HUP mDNSResponder"
echo ""
echo "然后测试："
echo "ping -c 1 mcp-bridge.local"
