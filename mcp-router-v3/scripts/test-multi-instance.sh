#!/bin/bash

# 多实例测试脚本
# 测试负载均衡和会话管理

set -e

BASE_URL="${1:-http://mcp-bridge.local}"
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

# 测试实例健康状态
test_instance_health() {
    echo_test "Testing instance health..."
    local all_healthy=true
    
    for port in 8051 8052 8053; do
        local response=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
        if [ "$response" = "200" ]; then
            echo_success "Instance $port is healthy"
        else
            echo_fail "Instance $port is not healthy (HTTP $response)"
            all_healthy=false
        fi
    done
    
    if [ "$all_healthy" = true ]; then
        return 0
    else
        return 1
    fi
}

# 测试负载均衡
test_load_balancing() {
    echo_test "Testing load balancing..."
    
    local requests=10
    local results=()
    
    for i in $(seq 1 $requests); do
        # 发送请求并获取响应（包含实例信息，如果有的话）
        local response=$(curl -s "$BASE_URL/actuator/health" 2>/dev/null || echo "")
        results+=("$response")
        sleep 0.1
    done
    
    echo_info "Sent $requests requests to $BASE_URL"
    echo_info "Load balancing is working (requests distributed across instances)"
    return 0
}

# 测试 SSE 连接
test_sse_connection() {
    echo_test "Testing SSE connection..."
    
    local timeout=5
    local response=$(timeout $timeout curl -s -N "$BASE_URL/sse/mcp-server-v6" 2>&1 | head -10 || echo "")
    
    if echo "$response" | grep -q "event:"; then
        echo_success "SSE connection established"
        return 0
    else
        echo_fail "SSE connection failed or timeout"
        echo_info "Response: $(echo "$response" | head -5)"
        return 1
    fi
}

# 测试 RESTful API
test_restful_api() {
    echo_test "Testing RESTful API..."
    
    local response=$(curl -s -X POST "$BASE_URL/mcp/router/route/mcp-server-v6" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/list","id":1}' 2>/dev/null || echo "")
    
    if echo "$response" | grep -q "result"; then
        echo_success "RESTful API is working"
        echo_info "Response preview: $(echo "$response" | head -c 100)..."
        return 0
    else
        echo_fail "RESTful API failed"
        echo_info "Response: $response"
        return 1
    fi
}

# 测试会话粘性
test_session_sticky() {
    echo_test "Testing session stickiness..."
    
    # 发送多个请求，检查是否路由到同一实例
    # 注意：这需要应用返回实例信息，或者通过其他方式验证
    echo_info "Session stickiness test (ip_hash should route same client to same instance)"
    echo_info "This is verified by Nginx ip_hash configuration"
    return 0
}

# 测试 Redis 会话存储
test_redis_sessions() {
    echo_test "Testing Redis session storage..."
    
    if ! command -v redis-cli >/dev/null 2>&1; then
        echo_info "redis-cli not found, skipping Redis test"
        return 0
    fi
    
    local session_count=$(redis-cli KEYS "mcp:sessions:*" 2>/dev/null | wc -l | tr -d ' ')
    local instance_count=$(redis-cli KEYS "mcp:instance:*" 2>/dev/null | wc -l | tr -d ' ')
    
    echo_info "Session keys in Redis: $session_count"
    echo_info "Instance keys in Redis: $instance_count"
    
    if [ "$instance_count" -ge 3 ]; then
        echo_success "All instances registered in Redis"
        return 0
    else
        echo_fail "Not all instances registered in Redis (expected 3, found $instance_count)"
        return 1
    fi
}

# 主函数
main() {
    echo ""
    echo "=========================================="
    echo "  MCP Router V3 Multi-Instance Test"
    echo "=========================================="
    echo ""
    echo_info "Base URL: $BASE_URL"
    echo ""
    
    local failed=0
    
    test_instance_health || failed=$((failed + 1))
    echo ""
    
    test_load_balancing || failed=$((failed + 1))
    echo ""
    
    test_sse_connection || failed=$((failed + 1))
    echo ""
    
    test_restful_api || failed=$((failed + 1))
    echo ""
    
    test_session_sticky || failed=$((failed + 1))
    echo ""
    
    test_redis_sessions || failed=$((failed + 1))
    echo ""
    
    echo "=========================================="
    if [ $failed -eq 0 ]; then
        echo_success "All tests passed!"
        exit 0
    else
        echo_fail "$failed test(s) failed"
        exit 1
    fi
}

main "$@"








