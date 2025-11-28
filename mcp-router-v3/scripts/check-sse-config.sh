#!/bin/bash

echo "=== 检查 SSE 连接配置 ==="
echo ""

# 1. 检查 Nginx 超时配置
echo "1. 检查 Nginx 超时配置..."
if [ -f "nginx/nginx.conf" ]; then
    READ_TIMEOUT=$(grep "proxy_read_timeout" nginx/nginx.conf | grep -v "^#" | awk '{print $2}' | tr -d ';')
    SEND_TIMEOUT=$(grep "proxy_send_timeout" nginx/nginx.conf | grep -v "^#" | awk '{print $2}' | tr -d ';')
    CONNECT_TIMEOUT=$(grep "proxy_connect_timeout" nginx/nginx.conf | grep -v "^#" | awk '{print $2}' | tr -d ';')
    
    echo "   proxy_read_timeout: ${READ_TIMEOUT:-未配置}"
    echo "   proxy_send_timeout: ${SEND_TIMEOUT:-未配置}"
    echo "   proxy_connect_timeout: ${CONNECT_TIMEOUT:-未配置}"
    
    if [ -n "$READ_TIMEOUT" ] && [[ "$READ_TIMEOUT" =~ 600s ]]; then
        echo "   ✅ 读取超时已配置为 600s (10分钟)"
    else
        echo "   ⚠️  读取超时可能过短（建议 600s）"
    fi
else
    echo "   ❌ Nginx 配置文件不存在"
fi
echo ""

# 2. 检查应用心跳配置
echo "2. 检查应用心跳配置..."
if [ -f "src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java" ]; then
    HEARTBEAT_INTERVAL=$(grep "Flux.interval(Duration.ofSeconds" src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java | grep heartbeat | head -1 | sed 's/.*Duration.ofSeconds(\([0-9]*\)).*/\1/')
    HEARTBEAT_TYPE=$(grep -A 2 "heartbeatFlux = Flux.interval" src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java | grep -E "event|comment" | head -1)
    
    echo "   心跳间隔: ${HEARTBEAT_INTERVAL:-未找到} 秒"
    if [ -n "$HEARTBEAT_INTERVAL" ] && [ "$HEARTBEAT_INTERVAL" = "15" ]; then
        echo "   ✅ 心跳间隔已优化为 15 秒"
    else
        echo "   ⚠️  心跳间隔可能需要优化（建议 15 秒）"
    fi
    
    if echo "$HEARTBEAT_TYPE" | grep -q "event"; then
        echo "   ✅ 心跳使用事件格式（event + data）"
    elif echo "$HEARTBEAT_TYPE" | grep -q "comment"; then
        echo "   ⚠️  心跳使用注释格式（可能不被所有客户端识别）"
    else
        echo "   ⚠️  无法确定心跳格式"
    fi
else
    echo "   ❌ 应用配置文件不存在"
fi
echo ""

# 3. 检查应用是否已重新编译
echo "3. 检查应用编译状态..."
if [ -f "target/mcp-router-v3-*.jar" ] || [ -d "target/classes" ]; then
    JAR_MODIFIED=$(find target -name "*.jar" -o -name "McpRouterServerConfig.class" 2>/dev/null | head -1 | xargs stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "未知")
    SRC_MODIFIED=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java 2>/dev/null || echo "未知")
    
    echo "   源代码修改时间: ${SRC_MODIFIED}"
    echo "   编译文件时间: ${JAR_MODIFIED}"
    
    if [ "$JAR_MODIFIED" != "未知" ] && [ "$SRC_MODIFIED" != "未知" ]; then
        if [ "$JAR_MODIFIED" \> "$SRC_MODIFIED" ] || [ "$JAR_MODIFIED" = "$SRC_MODIFIED" ]; then
            echo "   ✅ 应用已重新编译"
        else
            echo "   ⚠️  源代码已修改但可能未重新编译"
            echo "      请执行: mvn clean package"
        fi
    fi
else
    echo "   ⚠️  未找到编译文件，可能需要编译"
fi
echo ""

# 4. 检查 Nginx 是否运行
echo "4. 检查 Nginx 状态..."
if ps aux | grep -q "[n]ginx.*master"; then
    echo "   ✅ Nginx 正在运行"
    ps aux | grep "[n]ginx.*master" | head -1 | sed 's/^/      /'
else
    echo "   ❌ Nginx 未运行"
fi
echo ""

# 5. 检查应用实例
echo "5. 检查应用实例..."
APP_PORTS=(8051 8052 8053)
RUNNING_COUNT=0
for PORT in "${APP_PORTS[@]}"; do
    if lsof -i :${PORT} 2>/dev/null | grep -q LISTEN; then
        RUNNING_COUNT=$((RUNNING_COUNT + 1))
        echo "   ✅ 端口 ${PORT}: 运行中"
    else
        echo "   ❌ 端口 ${PORT}: 未运行"
    fi
done
echo "   运行实例数: ${RUNNING_COUNT}/3"
echo ""

echo "=== 配置检查完成 ==="
echo ""
echo "如果配置正确，运行测试："
echo "  ./scripts/test-sse-stability.sh http://mcp-bridge.test/sse/mcp-server-v6 300"
echo ""
