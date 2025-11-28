#!/bin/bash

echo "=== 启动 dnsmasq ==="
echo ""
echo "需要 sudo 权限来启动 dnsmasq 服务"
echo ""
echo "执行命令："
echo "sudo brew services start dnsmasq"
echo ""
echo "或者重启（如果已启动）："
echo "sudo brew services restart dnsmasq"
echo ""
echo "检查状态："
echo "brew services list | grep dnsmasq"
echo ""
read -p "是否现在启动？(y/n): " start_now

if [ "$start_now" = "y" ] || [ "$start_now" = "Y" ]; then
    sudo brew services start dnsmasq
    sleep 2
    if brew services list | grep -q "dnsmasq.*started"; then
        echo "✅ dnsmasq 已启动"
    else
        echo "❌ dnsmasq 启动失败"
    fi
else
    echo "请手动执行: sudo brew services start dnsmasq"
fi
