#!/bin/bash

echo "=== 配置本地 DNS（适用于所有客户端）==="
echo ""

DOMAIN="mcp-bridge.local"
IP="127.0.0.1"
HOSTS_FILE="/etc/hosts"

# 检查是否已存在
if grep -q "${DOMAIN}" "${HOSTS_FILE}" 2>/dev/null; then
    echo "✅ ${DOMAIN} 已在 ${HOSTS_FILE} 中"
    grep "${DOMAIN}" "${HOSTS_FILE}"
else
    echo "⚠️  ${DOMAIN} 不在 ${HOSTS_FILE} 中"
    echo ""
    echo "添加 ${DOMAIN} 到 ${HOSTS_FILE}（需要 sudo 权限）..."
    sudo sh -c "echo '${IP} ${DOMAIN}' >> ${HOSTS_FILE}"
    if [ $? -eq 0 ]; then
        echo "✅ 已添加"
    else
        echo "❌ 添加失败，请手动编辑 ${HOSTS_FILE}"
        exit 1
    fi
fi

echo ""
echo "刷新 DNS 缓存（需要 sudo 权限）..."
sudo dscacheutil -flushcache 2>/dev/null
sudo killall -HUP mDNSResponder 2>/dev/null
echo "✅ DNS 缓存已刷新"

echo ""
echo "测试 DNS 解析："
ping -c 1 ${DOMAIN} > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ DNS 解析正常"
else
    echo "⚠️  DNS 解析可能仍有问题，请检查配置"
fi

echo ""
echo "=== 配置完成 ==="
echo ""
echo "现在所有客户端都可以使用 ${DOMAIN} 了："
echo "- 浏览器: http://${DOMAIN}/admin"
echo "- curl: curl http://${DOMAIN}/mcp/router/tools/mcp-server-v6"
echo "- MCP Inspector: http://${DOMAIN}/sse/mcp-server-v6"
echo "- VSCode MCP 插件: http://${DOMAIN}/sse/mcp-server-v6"
