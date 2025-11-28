#!/bin/bash

echo "=== 修复 dnsmasq 状态问题 ==="
echo ""

# 检查 dnsmasq 是否实际运行
if ps aux | grep -q "[d]nsmasq"; then
    echo "✅ dnsmasq 进程正在运行"
    ps aux | grep "[d]nsmasq" | grep -v grep
    echo ""
    
    # 测试 dnsmasq 是否工作
    echo "测试 dnsmasq 功能..."
    RESULT=$(dig @127.0.0.1 mcp-bridge.local +short 2>&1 | head -1)
    if [ "$RESULT" = "127.0.0.1" ]; then
        echo "✅ dnsmasq 正常工作，可以解析 mcp-bridge.local"
        echo ""
        echo "虽然 brew services 显示状态不对，但 dnsmasq 实际在运行。"
        echo ""
        echo "解决方案："
        echo "1. 继续使用 dnsmasq（它已经在工作）"
        echo "2. 配置系统 DNS 为 127.0.0.1（见 docs/DNSMASQ_SETUP_GUIDE.md）"
        echo "3. 或者改用 .test 域名（更简单，不需要 dnsmasq）"
    else
        echo "❌ dnsmasq 未正确配置"
    fi
else
    echo "❌ dnsmasq 未运行"
    echo ""
    echo "尝试启动..."
    echo "sudo brew services start dnsmasq"
    echo ""
    echo "如果仍然失败，建议改用 .test 域名："
    echo "./scripts/switch-to-test-domain.sh"
fi
