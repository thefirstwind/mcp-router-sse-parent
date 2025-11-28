#!/bin/bash

echo "=== 快速修复 DNS 解析慢问题 ==="
echo ""
echo "问题：macOS 对 .local 域名优先使用 mDNS，导致 5 秒延迟"
echo ""
echo "请选择解决方案："
echo ""
echo "1. 安装和配置 dnsmasq（推荐，所有客户端受益）"
echo "2. 改用 .test 域名（简单，但需修改配置）"
echo "3. 仅刷新 DNS 缓存（临时，可能无效）"
echo ""
read -p "请选择 (1/2/3): " choice

case $choice in
    1)
        echo ""
        echo "=== 配置 dnsmasq ==="
        ./scripts/setup-dnsmasq.sh
        ;;
    2)
        echo ""
        echo "=== 改用 .test 域名 ==="
        DOMAIN="mcp-bridge.test"
        IP="127.0.0.1"
        
        # 检查是否已存在
        if grep -q "${DOMAIN}" /etc/hosts 2>/dev/null; then
            echo "✅ ${DOMAIN} 已在 /etc/hosts 中"
        else
            echo "添加 ${DOMAIN} 到 /etc/hosts..."
            sudo sh -c "echo '${IP} ${DOMAIN}' >> /etc/hosts"
            echo "✅ 已添加"
        fi
        
        echo ""
        echo "⚠️  需要修改 Nginx 配置："
        echo "   将 server_name mcp-bridge.local; 改为 server_name mcp-bridge.test;"
        echo ""
        echo "   然后重新加载 Nginx："
        echo "   sudo nginx -s reload -c $(pwd)/nginx/nginx.conf"
        echo ""
        echo "   之后使用: http://${DOMAIN}/mcp/router/tools/mcp-server-v6"
        ;;
    3)
        echo ""
        echo "=== 刷新 DNS 缓存 ==="
        sudo dscacheutil -flushcache
        sudo killall -HUP mDNSResponder
        echo "✅ DNS 缓存已刷新"
        echo ""
        echo "⚠️  注意：这通常不能解决问题，因为 macOS 仍然会优先使用 mDNS"
        echo "   建议使用方案 1 或 2"
        ;;
    *)
        echo "无效选择"
        exit 1
        ;;
esac

echo ""
echo "=== 验证 ==="
echo "运行验证脚本："
echo "./scripts/verify-all-clients.sh"
