#!/bin/bash

echo "=== 检查 Nginx 状态 ==="
echo ""

NGINX_CONF="nginx/nginx.conf"
NGINX_CONF_ABS="$(cd "$(dirname "$NGINX_CONF")" && pwd)/$(basename "$NGINX_CONF")"

# 1. 检查配置文件是否存在
echo "1. 检查配置文件..."
if [ -f "$NGINX_CONF" ]; then
    echo "   ✅ 配置文件存在: $NGINX_CONF"
    echo "   绝对路径: $NGINX_CONF_ABS"
else
    echo "   ❌ 配置文件不存在: $NGINX_CONF"
    exit 1
fi
echo ""

# 2. 测试配置文件语法
echo "2. 测试配置文件语法..."
if nginx -t -c "$NGINX_CONF_ABS" 2>&1; then
    echo "   ✅ 配置文件语法正确"
else
    echo "   ❌ 配置文件语法错误"
    echo ""
    echo "   详细错误信息："
    nginx -t -c "$NGINX_CONF_ABS" 2>&1 | grep -A 5 "error\|emerg" | sed 's/^/      /'
    exit 1
fi
echo ""

# 3. 检查 Nginx 进程
echo "3. 检查 Nginx 进程..."
NGINX_PIDS=$(ps aux | grep "[n]ginx" | awk '{print $2}')
if [ -n "$NGINX_PIDS" ]; then
    echo "   ✅ Nginx 进程正在运行:"
    ps aux | grep "[n]ginx" | grep -v grep | sed 's/^/      /'
else
    echo "   ❌ Nginx 未运行"
fi
echo ""

# 4. 检查端口占用
echo "4. 检查端口占用..."
if lsof -i :80 2>/dev/null | grep -q nginx; then
    echo "   ✅ 端口 80 被 Nginx 占用"
    lsof -i :80 2>/dev/null | grep nginx | head -3 | sed 's/^/      /'
else
    echo "   ⚠️  端口 80 未被 Nginx 占用"
    if lsof -i :80 2>/dev/null | head -3; then
        echo "   当前占用端口 80 的进程："
        lsof -i :80 2>/dev/null | head -3 | sed 's/^/      /'
    else
        echo "   端口 80 未被占用"
    fi
fi
echo ""

echo "=== 检查完成 ==="
echo ""
echo "如果配置正确但未运行，可以执行："
echo "  nginx -c $NGINX_CONF_ABS"
echo ""
echo "如果需要重新加载配置（Nginx 已运行）："
echo "  nginx -s reload -c $NGINX_CONF_ABS"
echo ""
