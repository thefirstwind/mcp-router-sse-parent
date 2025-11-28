#!/bin/bash

echo "=== 切换到 .test 域名（不需要 dnsmasq）==="
echo ""

DOMAIN="mcp-bridge.test"
NGINX_CONF="nginx/nginx.conf"

# 1. 添加 /etc/hosts
echo "1. 配置 /etc/hosts..."
if ! grep -q "${DOMAIN}" /etc/hosts 2>/dev/null; then
    sudo sh -c "echo '127.0.0.1 ${DOMAIN}' >> /etc/hosts"
    echo "   ✅ 已添加 ${DOMAIN} 到 /etc/hosts"
else
    echo "   ✅ ${DOMAIN} 已在 /etc/hosts 中"
fi
echo ""

# 2. 修改 Nginx 配置
echo "2. 修改 Nginx 配置..."
if grep -q "server_name mcp-bridge.local" "${NGINX_CONF}"; then
    sed -i '' 's/server_name mcp-bridge.local/server_name mcp-bridge.test/g' "${NGINX_CONF}"
    echo "   ✅ 已更新 Nginx 配置: server_name mcp-bridge.test"
elif grep -q "server_name mcp-bridge.test" "${NGINX_CONF}"; then
    echo "   ✅ Nginx 配置已经是 mcp-bridge.test"
else
    echo "   ⚠️  未找到 server_name 配置，请手动检查 ${NGINX_CONF}"
fi
echo ""

# 3. 重新加载 Nginx
echo "3. 重新加载 Nginx..."
if sudo nginx -s reload -c "$(pwd)/${NGINX_CONF}" 2>&1; then
    echo "   ✅ Nginx 已重新加载"
else
    echo "   ⚠️  Nginx 重新加载失败，请检查配置"
fi
echo ""

# 4. 刷新 DNS 缓存
echo "4. 刷新 DNS 缓存..."
sudo dscacheutil -flushcache 2>/dev/null || true
sudo killall -HUP mDNSResponder 2>/dev/null || true
echo "   ✅ DNS 缓存已刷新"
echo ""

# 5. 验证
echo "5. 验证配置..."
sleep 1
if dig +short ${DOMAIN} 2>&1 | grep -q "127.0.0.1"; then
    echo "   ✅ DNS 解析正常"
else
    echo "   ⚠️  DNS 解析可能有问题，但继续测试..."
fi
echo ""

echo "=== 完成 ==="
echo ""
echo "现在使用新域名："
echo "  http://${DOMAIN}/mcp/router/tools/mcp-server-v6"
echo ""
echo "测试命令："
echo "  curl http://${DOMAIN}/mcp/router/tools/mcp-server-v6"
echo ""
