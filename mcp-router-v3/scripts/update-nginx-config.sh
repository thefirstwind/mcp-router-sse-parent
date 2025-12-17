#!/bin/bash
# 更新 Nginx 配置文件

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
NGINX_CONF_SOURCE="$PROJECT_DIR/nginx/nginx.conf"

# 检测 Nginx 配置目录
if [ -d "/opt/homebrew/etc/nginx/servers" ]; then
    # macOS Homebrew
    NGINX_CONF_TARGET="/opt/homebrew/etc/nginx/servers/mcp-bridge.conf"
    NGINX_RELOAD_CMD="sudo nginx -s reload"
elif [ -d "/etc/nginx/conf.d" ]; then
    # Linux
    NGINX_CONF_TARGET="/etc/nginx/conf.d/mcp-bridge.conf"
    NGINX_RELOAD_CMD="sudo systemctl reload nginx"
else
    echo "❌ 未找到 Nginx 配置目录"
    exit 1
fi

echo "=== 更新 Nginx 配置文件 ==="
echo "源文件: $NGINX_CONF_SOURCE"
echo "目标文件: $NGINX_CONF_TARGET"
echo ""

# 检查源文件是否存在
if [ ! -f "$NGINX_CONF_SOURCE" ]; then
    echo "❌ 源配置文件不存在: $NGINX_CONF_SOURCE"
    exit 1
fi

# 复制配置文件
echo "复制配置文件..."
if sudo cp "$NGINX_CONF_SOURCE" "$NGINX_CONF_TARGET"; then
    echo "✅ 配置文件已复制"
else
    echo "❌ 复制配置文件失败，请手动执行:"
    echo "   sudo cp $NGINX_CONF_SOURCE $NGINX_CONF_TARGET"
    exit 1
fi

# 测试配置
echo ""
echo "测试 Nginx 配置..."
if sudo nginx -t; then
    echo "✅ Nginx 配置测试通过"
else
    echo "❌ Nginx 配置测试失败"
    exit 1
fi

# 询问是否重载
echo ""
read -p "是否立即重载 Nginx 配置? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if $NGINX_RELOAD_CMD; then
        echo "✅ Nginx 配置已重载"
    else
        echo "❌ Nginx 配置重载失败"
        exit 1
    fi
else
    echo "⚠️  请手动重载 Nginx 配置:"
    echo "   $NGINX_RELOAD_CMD"
fi

echo ""
echo "=== 验证配置 ==="
echo "检查关键配置项:"
grep -E "X-Forwarded-Host|proxy_buffering|proxy_read_timeout" "$NGINX_CONF_TARGET" | head -5















