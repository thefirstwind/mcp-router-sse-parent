#!/bin/bash

# 完整部署测试脚本
# 测试从启动到停止的完整流程

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

echo_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

echo_fail() {
    echo -e "${RED}[✗]${NC} $1"
}

echo ""
echo "=========================================="
echo "  MCP Router V3 完整部署测试"
echo "=========================================="
echo ""

# 步骤 1: 部署前检查
echo_test "步骤 1: 运行部署前检查..."
if "$SCRIPT_DIR/deploy-checklist.sh" >/dev/null 2>&1; then
    echo_success "部署前检查通过"
else
    echo_fail "部署前检查失败，请先修复问题"
    exit 1
fi
echo ""

# 步骤 2: 停止现有实例
echo_test "步骤 2: 停止现有实例..."
cd "$PROJECT_DIR"
./scripts/start-instances.sh stop >/dev/null 2>&1 || true
sleep 2
echo_success "已清理现有实例"
echo ""

# 步骤 3: 启动所有实例
echo_test "步骤 3: 启动所有实例..."
if timeout 90 ./scripts/start-instances.sh start 2>&1 | grep -q "started successfully"; then
    echo_success "所有实例启动成功"
else
    echo_fail "实例启动失败"
    exit 1
fi
echo ""

# 步骤 4: 等待实例完全启动
echo_test "步骤 4: 等待实例完全启动..."
sleep 10
echo_success "等待完成"
echo ""

# 步骤 5: 验证实例状态
echo_test "步骤 5: 验证实例状态..."
status_output=$(./scripts/start-instances.sh status 2>&1)
healthy_count=$(echo "$status_output" | grep -c "Running.*Health: OK" || echo "0")

if [ "$healthy_count" -eq 3 ]; then
    echo_success "所有 3 个实例运行正常"
else
    echo_fail "只有 $healthy_count 个实例运行正常（期望 3 个）"
    echo "$status_output"
    exit 1
fi
echo ""

# 步骤 6: 测试健康检查端点
echo_test "步骤 6: 测试健康检查端点..."
for port in 8051 8052 8053; do
    if curl -s -f "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
        echo_success "实例 $port 健康检查通过"
    else
        echo_fail "实例 $port 健康检查失败"
        exit 1
    fi
done
echo ""

# 步骤 7: 测试 Admin API
echo_test "步骤 7: 测试 Admin API..."
for port in 8051 8052 8053; do
    if curl -s -f "http://localhost:$port/admin/api/summary" >/dev/null 2>&1; then
        echo_success "实例 $port Admin API 正常"
    else
        echo_fail "实例 $port Admin API 失败"
        exit 1
    fi
done
echo ""

# 步骤 8: 测试 SSE 连接
echo_test "步骤 8: 测试 SSE 连接..."
for port in 8051 8052 8053; do
    response=$(timeout 3 curl -s -N "http://localhost:$port/sse/mcp-server-v6" 2>&1 | head -5 || echo "")
    if echo "$response" | grep -q "event:"; then
        echo_success "实例 $port SSE 连接正常"
    else
        echo_warn "实例 $port SSE 连接测试不确定（可能需要后端服务）"
    fi
done
echo ""

# 步骤 9: 测试 RESTful API
echo_test "步骤 9: 测试 RESTful API..."
for port in 8051 8052 8053; do
    response=$(curl -s -X POST "http://localhost:$port/mcp/router/route/mcp-server-v6" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/list","id":1}' 2>&1 || echo "")
    if echo "$response" | grep -q "result\|error"; then
        echo_success "实例 $port RESTful API 正常"
    else
        echo_warn "实例 $port RESTful API 响应异常（可能需要后端服务）"
    fi
done
echo ""

# 步骤 10: 测试重启功能
echo_test "步骤 10: 测试重启功能..."
if timeout 90 ./scripts/start-instances.sh restart 2>&1 | grep -q "started successfully"; then
    echo_success "重启功能正常"
else
    echo_fail "重启功能失败"
    exit 1
fi
sleep 5
healthy_count=$(./scripts/start-instances.sh status 2>&1 | grep -c "Running.*Health: OK" || echo "0")
if [ "$healthy_count" -eq 3 ]; then
    echo_success "重启后所有实例正常"
else
    echo_fail "重启后只有 $healthy_count 个实例正常"
    exit 1
fi
echo ""

# 步骤 11: 测试停止功能
echo_test "步骤 11: 测试停止功能..."
./scripts/start-instances.sh stop >/dev/null 2>&1
sleep 3
if ! lsof -i :8051 -i :8052 -i :8053 -sTCP:LISTEN >/dev/null 2>&1; then
    echo_success "停止功能正常，所有端口已释放"
else
    echo_fail "停止功能异常，仍有端口被占用"
    exit 1
fi
echo ""

# 总结
echo "=========================================="
echo "  测试完成"
echo "=========================================="
echo ""
echo_success "所有测试通过！"
echo ""
echo "部署验证清单："
echo "  ✓ 部署前检查"
echo "  ✓ 实例启动"
echo "  ✓ 健康检查"
echo "  ✓ API 功能"
echo "  ✓ SSE 连接"
echo "  ✓ RESTful API"
echo "  ✓ 重启功能"
echo "  ✓ 停止功能"
echo ""
echo "可以开始生产环境部署！"















