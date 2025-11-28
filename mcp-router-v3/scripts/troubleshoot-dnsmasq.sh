#!/bin/bash

echo "=== 排查 dnsmasq 启动问题 ==="
echo ""

BREW_PREFIX=$(brew --prefix)
CONFIG_FILE="${BREW_PREFIX}/etc/dnsmasq.conf"

# 1. 检查端口占用
echo "1. 检查端口 53 是否被占用..."
PORT_53=$(lsof -i :53 2>&1 | grep -v "COMMAND" | head -3)
if [ -n "$PORT_53" ]; then
    echo "   ⚠️  端口 53 被占用："
    echo "$PORT_53" | sed 's/^/      /'
    echo ""
    echo "   可能的原因："
    echo "   - macOS 系统 DNS 服务正在使用端口 53"
    echo "   - 其他 DNS 服务正在运行"
    echo ""
    echo "   解决方案："
    echo "   - 使用非标准端口（推荐）"
    echo "   - 或停止占用端口的服务"
else
    echo "   ✅ 端口 53 未被占用"
fi
echo ""

# 2. 检查配置文件语法
echo "2. 检查配置文件语法..."
if sudo "${BREW_PREFIX}/opt/dnsmasq/sbin/dnsmasq" --test -C "${CONFIG_FILE}" 2>&1; then
    echo "   ✅ 配置文件语法正确"
else
    echo "   ❌ 配置文件语法错误"
    echo "   请检查: ${CONFIG_FILE}"
fi
echo ""

# 3. 检查配置文件权限
echo "3. 检查配置文件权限..."
if [ -r "${CONFIG_FILE}" ]; then
    echo "   ✅ 配置文件可读"
    ls -l "${CONFIG_FILE}" | awk '{print "      " $0}'
else
    echo "   ❌ 配置文件不可读"
fi
echo ""

# 4. 尝试手动启动（前台模式，查看错误）
echo "4. 尝试手动启动（前台模式，查看错误）..."
echo "   执行: sudo ${BREW_PREFIX}/opt/dnsmasq/sbin/dnsmasq --keep-in-foreground -C ${CONFIG_FILE}"
echo "   （按 Ctrl+C 停止）"
echo ""
read -p "   是否现在尝试？(y/n): " try_now

if [ "$try_now" = "y" ] || [ "$try_now" = "Y" ]; then
    echo "   启动中...（5秒后自动停止）"
    timeout 5 sudo "${BREW_PREFIX}/opt/dnsmasq/sbin/dnsmasq" --keep-in-foreground -C "${CONFIG_FILE}" 2>&1 || true
    echo ""
fi
echo ""

# 5. 检查日志
echo "5. 检查日志文件..."
LOG_FILE="${BREW_PREFIX}/var/log/dnsmasq.log"
if [ -f "${LOG_FILE}" ]; then
    echo "   最后 10 行日志："
    tail -10 "${LOG_FILE}" | sed 's/^/      /'
else
    echo "   ⚠️  日志文件不存在: ${LOG_FILE}"
fi
echo ""

# 6. 提供解决方案
echo "=== 解决方案 ==="
echo ""
echo "如果端口 53 被占用，可以使用非标准端口："
echo ""
echo "1. 修改配置文件 ${CONFIG_FILE}，添加："
echo "   port=5353"
echo ""
echo "2. 配置系统 DNS 时使用 127.0.0.1:5353"
echo ""
echo "或者使用更简单的方案：改用 .test 域名（不需要 dnsmasq）"
echo ""
