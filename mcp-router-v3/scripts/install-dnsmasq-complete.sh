#!/bin/bash

set -e

echo "=== 完整配置 dnsmasq 解决 DNS 解析慢问题 ==="
echo ""

DOMAIN="mcp-bridge.local"
BREW_PREFIX=$(brew --prefix)
CONFIG_FILE="${BREW_PREFIX}/etc/dnsmasq.conf"
HOSTS_ENTRY="127.0.0.1 ${DOMAIN}"

# 1. 检查 Homebrew
echo "1. 检查 Homebrew..."
if ! command -v brew &> /dev/null; then
    echo "   ❌ 未找到 Homebrew"
    echo "   请先安装 Homebrew: https://brew.sh"
    exit 1
fi
echo "   ✅ Homebrew 已安装 (${BREW_PREFIX})"
echo ""

# 2. 安装 dnsmasq
echo "2. 安装 dnsmasq..."
if brew list dnsmasq &> /dev/null; then
    echo "   ✅ dnsmasq 已安装"
else
    echo "   正在安装 dnsmasq..."
    brew install dnsmasq
    echo "   ✅ dnsmasq 安装完成"
fi
echo ""

# 3. 配置 dnsmasq
echo "3. 配置 dnsmasq..."
if [ ! -f "${CONFIG_FILE}" ]; then
    echo "   ⚠️  配置文件不存在，创建默认配置..."
    mkdir -p "$(dirname "${CONFIG_FILE}")"
    # 创建基本配置文件
    cat > "${CONFIG_FILE}" << 'CONFIG_EOF'
# dnsmasq 配置文件
# 监听本地
listen-address=127.0.0.1
# 不使用 /etc/resolv.conf
no-resolv
# 使用 Google DNS 作为上游
server=8.8.8.8
server=8.8.4.4
CONFIG_EOF
    echo "   ✅ 配置文件已创建"
fi

# 检查是否已配置 .local 域名
if grep -q "address=/.local/127.0.0.1" "${CONFIG_FILE}" 2>/dev/null; then
    echo "   ✅ .local 域名配置已存在"
else
    echo "   添加 .local 域名配置..."
    echo "" >> "${CONFIG_FILE}"
    echo "# MCP Router: 自动解析所有 .local 域名到 127.0.0.1" >> "${CONFIG_FILE}"
    echo "address=/.local/127.0.0.1" >> "${CONFIG_FILE}"
    echo "   ✅ 配置已添加"
fi

# 检查是否已配置特定域名
if grep -q "address=/${DOMAIN}/127.0.0.1" "${CONFIG_FILE}" 2>/dev/null; then
    echo "   ✅ ${DOMAIN} 配置已存在"
else
    echo "   添加 ${DOMAIN} 配置..."
    echo "address=/${DOMAIN}/127.0.0.1" >> "${CONFIG_FILE}"
    echo "   ✅ 配置已添加"
fi

echo "   配置文件位置: ${CONFIG_FILE}"
echo ""

# 4. 确保 /etc/hosts 也有配置（双重保险）
echo "4. 配置 /etc/hosts（双重保险）..."
if grep -q "${DOMAIN}" /etc/hosts 2>/dev/null; then
    echo "   ✅ ${DOMAIN} 已在 /etc/hosts 中"
else
    echo "   添加 ${DOMAIN} 到 /etc/hosts..."
    sudo sh -c "echo '${HOSTS_ENTRY}' >> /etc/hosts"
    echo "   ✅ 已添加"
fi
echo ""

# 5. 启动 dnsmasq
echo "5. 启动 dnsmasq..."
if brew services list | grep -q "dnsmasq.*started"; then
    echo "   ✅ dnsmasq 正在运行"
    echo "   如果需要重启: brew services restart dnsmasq"
else
    echo "   启动 dnsmasq（需要 sudo 权限）..."
    sudo brew services start dnsmasq
    sleep 2
    if brew services list | grep -q "dnsmasq.*started"; then
        echo "   ✅ dnsmasq 已启动"
    else
        echo "   ❌ dnsmasq 启动失败"
        echo "   请检查配置: ${CONFIG_FILE}"
        echo "   或手动启动: sudo brew services start dnsmasq"
        exit 1
    fi
fi
echo ""

# 6. 检查系统 DNS 配置
echo "6. 检查系统 DNS 配置..."
CURRENT_DNS_WIFI=$(networksetup -getdnsservers Wi-Fi 2>/dev/null | head -1 || echo "")
CURRENT_DNS_ETHERNET=$(networksetup -getdnsservers Ethernet 2>/dev/null | head -1 || echo "")

if [ "$CURRENT_DNS_WIFI" = "127.0.0.1" ] || [ "$CURRENT_DNS_ETHERNET" = "127.0.0.1" ]; then
    echo "   ✅ 系统 DNS 已配置为使用 dnsmasq (127.0.0.1)"
    if [ "$CURRENT_DNS_WIFI" = "127.0.0.1" ]; then
        echo "      Wi-Fi: 127.0.0.1"
    fi
    if [ "$CURRENT_DNS_ETHERNET" = "127.0.0.1" ]; then
        echo "      Ethernet: 127.0.0.1"
    fi
else
    echo "   ⚠️  系统 DNS 未配置为使用 dnsmasq"
    echo ""
    echo "   请按以下步骤配置系统 DNS："
    echo ""
    echo "   方法 A：通过系统偏好设置（推荐）"
    echo "   1. 打开：系统偏好设置 > 网络"
    echo "   2. 选择当前网络（Wi-Fi 或 Ethernet）"
    echo "   3. 点击'高级' > 'DNS'"
    echo "   4. 点击左下角的 '+' 添加 DNS 服务器"
    echo "   5. 输入: 127.0.0.1"
    echo "   6. 将 127.0.0.1 拖到列表最前面（最重要！）"
    echo "   7. 点击'好'保存"
    echo ""
    echo "   方法 B：通过命令行（需要管理员权限）"
    if [ -n "$CURRENT_DNS_WIFI" ]; then
        echo "   sudo networksetup -setdnsservers Wi-Fi 127.0.0.1"
    fi
    if [ -n "$CURRENT_DNS_ETHERNET" ]; then
        echo "   sudo networksetup -setdnsservers Ethernet 127.0.0.1"
    fi
    echo ""
    read -p "   是否现在通过命令行配置？(y/n): " configure_now
    if [ "$configure_now" = "y" ] || [ "$configure_now" = "Y" ]; then
        if [ -n "$CURRENT_DNS_WIFI" ]; then
            echo "   配置 Wi-Fi DNS..."
            sudo networksetup -setdnsservers Wi-Fi 127.0.0.1
        fi
        if [ -n "$CURRENT_DNS_ETHERNET" ]; then
            echo "   配置 Ethernet DNS..."
            sudo networksetup -setdnsservers Ethernet 127.0.0.1
        fi
        echo "   ✅ DNS 配置完成"
    fi
fi
echo ""

# 7. 刷新 DNS 缓存
echo "7. 刷新 DNS 缓存..."
sudo dscacheutil -flushcache 2>/dev/null || true
sudo killall -HUP mDNSResponder 2>/dev/null || true
echo "   ✅ DNS 缓存已刷新"
echo ""

# 8. 验证配置
echo "8. 验证配置..."
echo "   测试 dnsmasq 是否响应..."
if dig @127.0.0.1 ${DOMAIN} +short 2>/dev/null | grep -q "127.0.0.1"; then
    echo "   ✅ dnsmasq 正常工作"
else
    echo "   ⚠️  dnsmasq 可能未正确配置，但继续测试..."
fi
echo ""

echo "   测试 DNS 解析速度..."
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
    echo "      请检查 dnsmasq 配置和系统 DNS 设置"
fi
echo ""

# 9. 总结
echo "=== 配置完成 ==="
echo ""
echo "如果 DNS 解析仍然慢，请："
echo "1. 确认系统 DNS 已配置为 127.0.0.1（最重要！）"
echo "2. 重启 dnsmasq: sudo brew services restart dnsmasq"
echo "3. 再次刷新 DNS 缓存: sudo dscacheutil -flushcache && sudo killall -HUP mDNSResponder"
echo ""
echo "验证命令："
echo "  time dig +short ${DOMAIN}"
echo "  time curl http://${DOMAIN}/mcp/router/tools/mcp-server-v6"
echo ""
