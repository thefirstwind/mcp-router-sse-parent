#!/bin/bash

echo "=== 修复 .local 域名 DNS 解析 ==="
echo ""

DOMAIN="mcp-bridge.local"
IP="127.0.0.1"
HOSTS_FILE="/etc/hosts"

# 检查是否已存在
if grep -q "${DOMAIN}" "${HOSTS_FILE}" 2>/dev/null; then
    echo "✅ ${DOMAIN} 已在 ${HOSTS_FILE} 中"
    grep "${DOMAIN}" "${HOSTS_FILE}"
    echo ""
    echo "如需更新，请手动编辑 ${HOSTS_FILE}"
else
    echo "⚠️  ${DOMAIN} 不在 ${HOSTS_FILE} 中"
    echo ""
    echo "请运行以下命令添加（需要 sudo 权限）："
    echo "sudo sh -c 'echo \"${IP} ${DOMAIN}\" >> ${HOSTS_FILE}'"
    echo ""
    echo "或者手动编辑 ${HOSTS_FILE}，添加："
    echo "${IP} ${DOMAIN}"
fi

echo ""
echo "添加后，测试 DNS 解析："
echo "ping -c 1 ${DOMAIN}"
echo ""
echo "然后重新运行排查脚本："
echo "./scripts/debug-restful-domain.sh"
