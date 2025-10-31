#!/bin/bash

# Nacos Ephemeral 实例修复测试脚本
# 用途: 验证服务崩溃后实例能否自动从 Nacos 清理

set -e

NACOS_SERVER="http://127.0.0.1:8848"
ROUTER_SERVICE="mcp-router-v3"
SERVER_SERVICE="mcp-server-v6"
SERVICE_GROUP="mcp-server"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "  Nacos Ephemeral 实例修复测试"
echo "=========================================="
echo ""

# 1. 检查服务是否运行
echo -e "${YELLOW}[1/6]${NC} 检查服务运行状态..."
ROUTER_PID=$(jps | grep 'mcp-router-v3' | awk '{print $1}' || echo "")
SERVER_PID=$(jps | grep 'mcp-server-v6' | awk '{print $1}' || echo "")

if [ -z "$ROUTER_PID" ] || [ -z "$SERVER_PID" ]; then
    echo -e "${RED}✗ 服务未运行，请先启动服务${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Router PID: $ROUTER_PID${NC}"
echo -e "${GREEN}✓ Server PID: $SERVER_PID${NC}"
echo ""

# 2. 检查 Nacos 中的实例数量（崩溃前）
echo -e "${YELLOW}[2/6]${NC} 检查 Nacos 注册状态（崩溃前）..."

get_instance_count() {
    local service=$1
    local group=$2
    curl -s "${NACOS_SERVER}/nacos/v1/ns/instance/list?serviceName=${service}&groupName=${group}" \
        | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data.get('hosts', [])))" 2>/dev/null || echo "0"
}

check_ephemeral() {
    local service=$1
    local group=$2
    curl -s "${NACOS_SERVER}/nacos/v1/ns/instance/list?serviceName=${service}&groupName=${group}" \
        | python3 -c "import sys,json; data=json.load(sys.stdin); hosts=data.get('hosts', []); print('true' if hosts and hosts[0].get('ephemeral') else 'false')" 2>/dev/null || echo "false"
}

ROUTER_COUNT_BEFORE=$(get_instance_count "$ROUTER_SERVICE" "$SERVICE_GROUP")
SERVER_COUNT_BEFORE=$(get_instance_count "$SERVER_SERVICE" "$SERVICE_GROUP")

echo "  Router 实例数: $ROUTER_COUNT_BEFORE"
echo "  Server 实例数: $SERVER_COUNT_BEFORE"

if [ "$ROUTER_COUNT_BEFORE" -eq 0 ] || [ "$SERVER_COUNT_BEFORE" -eq 0 ]; then
    echo -e "${RED}✗ 服务未在 Nacos 中注册${NC}"
    exit 1
fi

# 3. 验证 ephemeral 属性
echo ""
echo -e "${YELLOW}[3/6]${NC} 验证 ephemeral 属性..."

ROUTER_EPHEMERAL=$(check_ephemeral "$ROUTER_SERVICE" "$SERVICE_GROUP")
SERVER_EPHEMERAL=$(check_ephemeral "$SERVER_SERVICE" "$SERVICE_GROUP")

if [ "$ROUTER_EPHEMERAL" = "true" ]; then
    echo -e "${GREEN}✓ Router ephemeral: true${NC}"
else
    echo -e "${RED}✗ Router ephemeral: false (修复失败!)${NC}"
    exit 1
fi

if [ "$SERVER_EPHEMERAL" = "true" ]; then
    echo -e "${GREEN}✓ Server ephemeral: true${NC}"
else
    echo -e "${RED}✗ Server ephemeral: false (修复失败!)${NC}"
    exit 1
fi

# 4. 模拟崩溃
echo ""
echo -e "${YELLOW}[4/6]${NC} 模拟服务崩溃 (kill -9)..."
echo "  终止 Router (PID: $ROUTER_PID)"
echo "  终止 Server (PID: $SERVER_PID)"

kill -9 $ROUTER_PID $SERVER_PID 2>/dev/null || true
echo -e "${GREEN}✓ 服务已强制终止${NC}"

# 5. 等待 Nacos 清理实例
echo ""
echo -e "${YELLOW}[5/6]${NC} 等待 Nacos 自动清理实例..."
echo "  Nacos 临时实例清理时间: 15-30 秒"

for i in {1..6}; do
    echo -n "  等待中... ${i}0 秒"
    sleep 5
    
    ROUTER_COUNT=$(get_instance_count "$ROUTER_SERVICE" "$SERVICE_GROUP")
    SERVER_COUNT=$(get_instance_count "$SERVER_SERVICE" "$SERVICE_GROUP")
    
    if [ "$ROUTER_COUNT" -eq 0 ] && [ "$SERVER_COUNT" -eq 0 ]; then
        echo -e " ${GREEN}✓ 实例已清理${NC}"
        break
    fi
    
    echo " (Router: $ROUTER_COUNT, Server: $SERVER_COUNT)"
done

# 6. 验证结果
echo ""
echo -e "${YELLOW}[6/6]${NC} 验证清理结果..."

ROUTER_COUNT_AFTER=$(get_instance_count "$ROUTER_SERVICE" "$SERVICE_GROUP")
SERVER_COUNT_AFTER=$(get_instance_count "$SERVER_SERVICE" "$SERVICE_GROUP")

echo "  Router 实例数: $ROUTER_COUNT_BEFORE → $ROUTER_COUNT_AFTER"
echo "  Server 实例数: $SERVER_COUNT_BEFORE → $SERVER_COUNT_AFTER"

# 最终结果
echo ""
echo "=========================================="
if [ "$ROUTER_COUNT_AFTER" -eq 0 ] && [ "$SERVER_COUNT_AFTER" -eq 0 ]; then
    echo -e "${GREEN}✓ 测试通过！${NC}"
    echo -e "${GREEN}  所有实例已自动清理，ephemeral 修复成功！${NC}"
    echo "=========================================="
    exit 0
else
    echo -e "${RED}✗ 测试失败！${NC}"
    echo -e "${RED}  仍有 $((ROUTER_COUNT_AFTER + SERVER_COUNT_AFTER)) 个实例残留${NC}"
    
    if [ "$ROUTER_COUNT_AFTER" -gt 0 ]; then
        echo -e "${RED}  - Router: $ROUTER_COUNT_AFTER 个实例${NC}"
    fi
    
    if [ "$SERVER_COUNT_AFTER" -gt 0 ]; then
        echo -e "${RED}  - Server: $SERVER_COUNT_AFTER 个实例${NC}"
    fi
    
    echo "=========================================="
    echo "可能的原因:"
    echo "  1. ephemeral 属性未正确设置"
    echo "  2. Nacos 配置的清理时间较长"
    echo "  3. 网络延迟或 Nacos 服务异常"
    echo ""
    echo "建议操作:"
    echo "  1. 检查服务注册代码中的 setEphemeral(true)"
    echo "  2. 查看 Nacos 日志: /Users/shine/logs/nacos/"
    echo "  3. 手动清理残留实例: curl -X DELETE '${NACOS_SERVER}/nacos/v1/ns/instance?...'"
    exit 1
fi


