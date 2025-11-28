#!/bin/bash

echo "=== 诊断 Nginx 状态 ==="
echo ""

# 1. 检查 Nginx 进程
echo "1. 检查 Nginx 进程..."
ps aux | grep "[n]ginx" | while read line; do
    echo "   $line"
done

if ! ps aux | grep -q "[n]ginx"; then
    echo "   ⚠️  没有运行中的 Nginx 进程"
fi
echo ""

# 2. 检查端口 80
echo "2. 检查端口 80..."
lsof -i :80 2>/dev/null | head -5
echo ""

# 3. 检查配置文件
echo "3. 检查配置文件..."
NGINX_CONF="$(pwd)/nginx/nginx.conf"
if [ -f "$NGINX_CONF" ]; then
    echo "   ✅ 配置文件存在: $NGINX_CONF"
    echo "   配置文件内容摘要："
    echo "   - events 块: $(grep -c 'events {' "$NGINX_CONF" || echo 0)"
    echo "   - http 块: $(grep -c '^http {' "$NGINX_CONF" || echo 0)"
    echo "   - upstream: $(grep -c 'upstream' "$NGINX_CONF" || echo 0)"
    echo "   - server: $(grep -c '^    server {' "$NGINX_CONF" || echo 0)"
else
    echo "   ❌ 配置文件不存在"
fi
echo ""

# 4. 检查 Nginx 使用的配置文件
echo "4. 检查 Nginx 使用的配置文件..."
if ps aux | grep -q "[n]ginx.*master"; then
    MASTER_PID=$(ps aux | grep "[n]ginx.*master" | awk '{print $2}' | head -1)
    if [ -n "$MASTER_PID" ]; then
        echo "   Master PID: $MASTER_PID"
        # 尝试查找配置文件
        NGINX_CMD=$(ps -p $MASTER_PID -o command= 2>/dev/null)
        if [ -n "$NGINX_CMD" ]; then
            echo "   命令: $NGINX_CMD"
            # 提取配置文件路径
            if echo "$NGINX_CMD" | grep -q "\-c"; then
                CONFIG_PATH=$(echo "$NGINX_CMD" | sed -n 's/.*-c \([^ ]*\).*/\1/p')
                echo "   使用的配置文件: $CONFIG_PATH"
            else
                echo "   ⚠️  未指定配置文件（使用默认配置）"
            fi
        fi
    fi
else
    echo "   ⚠️  未找到 Nginx master 进程"
fi
echo ""

# 5. 测试配置文件
echo "5. 测试项目配置文件..."
NGINX_CONF="$(pwd)/nginx/nginx.conf"
if nginx -t -c "$NGINX_CONF" 2>&1 | grep -q "syntax is ok"; then
    echo "   ✅ 配置文件语法正确"
else
    echo "   ❌ 配置文件语法错误"
    nginx -t -c "$NGINX_CONF" 2>&1 | grep -E "error|emerg" | head -3 | sed 's/^/      /'
fi
echo ""

echo "=== 建议 ==="
echo ""
echo "如果 Nginx 使用了不同的配置文件，需要："
echo "1. 停止现有 Nginx: sudo pkill nginx"
echo "2. 使用项目配置启动: sudo nginx -c $(pwd)/nginx/nginx.conf"
echo ""
echo "或者如果配置文件正确，重新加载："
echo "  sudo nginx -s reload -c $(pwd)/nginx/nginx.conf"
echo ""
