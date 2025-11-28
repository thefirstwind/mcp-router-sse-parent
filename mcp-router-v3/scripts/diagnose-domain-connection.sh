#!/bin/bash

# 诊断域名连接问题

DOMAIN="mcp-bridge.test"
CONTEXT_PATH="/mcp-bridge"

echo "=== 诊断域名连接问题 ==="
echo ""

# 1. 检查 DNS 解析
echo "1. 检查 DNS 解析..."
if dig +short ${DOMAIN} 2>&1 | grep -q "127.0.0.1"; then
    echo "   ✅ DNS 解析正常: ${DOMAIN} -> 127.0.0.1"
else
    echo "   ❌ DNS 解析失败"
    echo "   请检查 /etc/hosts 文件"
fi
echo ""

# 2. 检查 Nginx 状态
echo "2. 检查 Nginx 状态..."
if ps aux | grep -E "nginx.*master" | grep -v grep > /dev/null; then
    echo "   ✅ Nginx 正在运行"
else
    echo "   ❌ Nginx 未运行"
fi
echo ""

# 3. 检查应用状态
echo "3. 检查应用状态..."
HEALTH=$(curl -s http://${DOMAIN}${CONTEXT_PATH}/actuator/health 2>&1)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "   ✅ 应用健康检查通过"
else
    echo "   ❌ 应用健康检查失败"
    echo "   响应: $HEALTH"
fi
echo ""

# 4. 测试 SSE 连接
echo "4. 测试 SSE 连接..."
SSE_RESPONSE=$(timeout 3 curl -N -H "Accept: text/event-stream" \
    http://${DOMAIN}${CONTEXT_PATH}/sse/mcp-server-v6 2>&1 | head -5)

if echo "$SSE_RESPONSE" | grep -q "event:endpoint"; then
    echo "   ✅ SSE 连接成功"
    ENDPOINT=$(echo "$SSE_RESPONSE" | grep "data:http" | head -1 | sed 's/data://')
    echo "   Endpoint: $ENDPOINT"
    
    # 检查 endpoint 是否包含 context-path
    if echo "$ENDPOINT" | grep -q "${CONTEXT_PATH}"; then
        echo "   ✅ Endpoint 包含正确的 context-path"
    else
        echo "   ⚠️  Endpoint 缺少 context-path"
    fi
else
    echo "   ❌ SSE 连接失败"
    echo "   响应: $SSE_RESPONSE"
fi
echo ""

# 5. 测试 Streamable 连接
echo "5. 测试 Streamable 连接..."
STREAMABLE_RESPONSE=$(timeout 3 curl -N -H "Accept: application/x-ndjson" \
    http://${DOMAIN}${CONTEXT_PATH}/mcp/mcp-server-v6 2>&1 | head -5)

if echo "$STREAMABLE_RESPONSE" | grep -q '"event":"endpoint"'; then
    echo "   ✅ Streamable 连接成功"
    ENDPOINT=$(echo "$STREAMABLE_RESPONSE" | grep -o '"data":"[^"]*"' | head -1 | sed 's/"data":"//;s/"$//')
    echo "   Endpoint: $ENDPOINT"
    
    # 检查 endpoint 是否包含 context-path
    if echo "$ENDPOINT" | grep -q "${CONTEXT_PATH}"; then
        echo "   ✅ Endpoint 包含正确的 context-path"
    else
        echo "   ⚠️  Endpoint 缺少 context-path"
    fi
else
    echo "   ❌ Streamable 连接失败"
    echo "   响应: $STREAMABLE_RESPONSE"
fi
echo ""

# 6. 检查心跳
echo "6. 检查心跳..."
HEARTBEAT_TEST=$(timeout 20 curl -N -H "Accept: text/event-stream" \
    http://${DOMAIN}${CONTEXT_PATH}/sse/mcp-server-v6 2>&1 | grep -E "heartbeat|event:" | head -5)

if echo "$HEARTBEAT_TEST" | grep -q "heartbeat"; then
    echo "   ✅ 心跳正常"
else
    echo "   ⚠️  未检测到心跳（可能需要等待 15 秒）"
    echo "   响应: $HEARTBEAT_TEST"
fi
echo ""

# 7. 检查 Nginx 配置
echo "7. 检查 Nginx 配置..."
if [ -f "nginx/nginx.conf" ]; then
    if grep -q "server_name ${DOMAIN}" nginx/nginx.conf; then
        echo "   ✅ Nginx 配置包含正确的域名"
    else
        echo "   ⚠️  Nginx 配置可能不正确"
    fi
    
    if grep -q "X-Forwarded-Prefix" nginx/nginx.conf; then
        echo "   ✅ Nginx 配置包含 X-Forwarded-Prefix 头"
    else
        echo "   ⚠️  Nginx 配置缺少 X-Forwarded-Prefix 头"
    fi
else
    echo "   ⚠️  未找到 Nginx 配置文件"
fi
echo ""

echo "=== 诊断完成 ==="
echo ""
echo "如果连接失败，请检查："
echo "1. Nginx 是否正在运行: ps aux | grep nginx"
echo "2. 应用是否正在运行: ps aux | grep mcp-router"
echo "3. /etc/hosts 是否包含: 127.0.0.1 ${DOMAIN}"
echo "4. Nginx 配置是否正确: cat nginx/nginx.conf | grep -A 20 'server_name'"
echo "5. 应用日志: tail -50 logs/mcp-router-v3.log"

