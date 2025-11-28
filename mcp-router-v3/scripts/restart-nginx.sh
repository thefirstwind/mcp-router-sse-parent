#!/bin/bash

echo "=== 重启 Nginx（使用项目配置）==="
echo ""

NGINX_CONF="$(pwd)/nginx/nginx.conf"

# 1. 检查配置文件
echo "1. 检查配置文件..."
if [ ! -f "$NGINX_CONF" ]; then
    echo "   ❌ 配置文件不存在: $NGINX_CONF"
    exit 1
fi
echo "   ✅ 配置文件存在: $NGINX_CONF"
echo ""

# 2. 测试配置文件
echo "2. 测试配置文件语法..."
if sudo nginx -t -c "$NGINX_CONF" 2>&1 | grep -q "syntax is ok"; then
    echo "   ✅ 配置文件语法正确"
else
    echo "   ❌ 配置文件语法错误"
    sudo nginx -t -c "$NGINX_CONF" 2>&1 | grep -E "error|emerg" | head -5
    exit 1
fi
echo ""

# 3. 停止所有 Nginx 进程
echo "3. 停止现有 Nginx 进程..."
NGINX_PIDS=$(ps aux | grep "[n]ginx" | awk '{print $2}')
if [ -n "$NGINX_PIDS" ]; then
    echo "   发现 Nginx 进程: $NGINX_PIDS"
    echo "   正在停止..."
    sudo pkill nginx
    sleep 2
    
    # 检查是否还有进程
    if ps aux | grep -q "[n]ginx"; then
        echo "   ⚠️  强制停止..."
        sudo pkill -9 nginx
        sleep 1
    fi
    
    if ps aux | grep -q "[n]ginx"; then
        echo "   ❌ 无法停止 Nginx 进程"
        exit 1
    else
        echo "   ✅ Nginx 已停止"
    fi
else
    echo "   ✅ 没有运行中的 Nginx 进程"
fi
echo ""

# 4. 确保日志目录存在
echo "4. 准备日志目录..."
mkdir -p logs
if [ -d "logs" ]; then
    echo "   ✅ 日志目录存在"
else
    echo "   ❌ 无法创建日志目录"
    exit 1
fi
echo ""

# 5. 启动 Nginx
echo "5. 启动 Nginx..."
if sudo nginx -c "$NGINX_CONF" 2>&1; then
    echo "   ✅ Nginx 启动命令执行成功"
    sleep 1
    
    # 检查进程
    if ps aux | grep -q "[n]ginx"; then
        echo "   ✅ Nginx 进程正在运行"
        ps aux | grep "[n]ginx" | grep -v grep | sed 's/^/      /'
    else
        echo "   ⚠️  Nginx 启动后立即退出"
        echo "   检查错误日志..."
        if [ -f "logs/nginx-error.log" ]; then
            tail -10 logs/nginx-error.log | sed 's/^/      /'
        fi
        exit 1
    fi
else
    echo "   ❌ Nginx 启动失败"
    if [ -f "logs/nginx-error.log" ]; then
        echo "   错误日志："
        tail -10 logs/nginx-error.log | sed 's/^/      /'
    fi
    exit 1
fi
echo ""

# 6. 检查端口
echo "6. 检查端口 80..."
if lsof -i :80 2>/dev/null | grep -q nginx; then
    echo "   ✅ 端口 80 被 Nginx 占用"
else
    echo "   ⚠️  端口 80 未被 Nginx 占用"
fi
echo ""

echo "=== 重启完成 ==="
echo ""
echo "Nginx 状态："
ps aux | grep "[n]ginx" | grep -v grep | head -3
echo ""
echo "测试配置："
echo "  curl http://mcp-bridge.test/actuator/health"
echo ""
