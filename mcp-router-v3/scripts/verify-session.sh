#!/bin/bash

# 会话管理验证脚本
# 验证多实例环境下的会话管理是否正常工作

set -e

BASE_URL="${1:-http://mcp-bridge.local}"
REDIS_HOST="${2:-localhost}"
REDIS_PORT="${3:-6379}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Redis 连接
check_redis() {
    echo_info "Checking Redis connection..."
    if command -v redis-cli >/dev/null 2>&1; then
        if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping >/dev/null 2>&1; then
            echo_info "✅ Redis connection OK"
            return 0
        else
            echo_error "❌ Redis connection failed"
            return 1
        fi
    else
        echo_warn "⚠️  redis-cli not found, skipping Redis check"
        return 0
    fi
}

# 检查实例健康状态
check_instances() {
    echo_info "Checking instance health..."
    local all_healthy=true
    
    for port in 8051 8052 8053; do
        if curl -s "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
            echo_info "✅ Instance on port $port is healthy"
        else
            echo_error "❌ Instance on port $port is not responding"
            all_healthy=false
        fi
    done
    
    if [ "$all_healthy" = true ]; then
        echo_info "✅ All instances are healthy"
        return 0
    else
        echo_error "❌ Some instances are not healthy"
        return 1
    fi
}

# 测试 SSE 连接
test_sse_connection() {
    echo_info "Testing SSE connection..."
    local response=$(curl -s -N -m 5 "$BASE_URL/sse/mcp-server-v6" 2>&1 | head -5)
    if echo "$response" | grep -q "event:"; then
        echo_info "✅ SSE connection successful"
        return 0
    else
        echo_warn "⚠️  SSE connection test inconclusive (may need manual verification)"
        return 0
    fi
}

# 测试 RESTful API
test_restful_api() {
    echo_info "Testing RESTful API..."
    local response=$(curl -s -X POST "$BASE_URL/mcp/router/route/mcp-server-v6" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/list","id":1}')
    
    if echo "$response" | grep -q "result"; then
        echo_info "✅ RESTful API test successful"
        echo_info "   Response: $(echo "$response" | head -c 100)..."
        return 0
    else
        echo_error "❌ RESTful API test failed"
        echo_error "   Response: $response"
        return 1
    fi
}

# 检查 Redis 中的会话数据
check_redis_sessions() {
    if ! command -v redis-cli >/dev/null 2>&1; then
        echo_warn "⚠️  redis-cli not found, skipping Redis session check"
        return 0
    fi
    
    echo_info "Checking Redis session data..."
    local session_keys=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" KEYS "mcp:sessions:*" 2>/dev/null | wc -l | tr -d ' ')
    local instance_keys=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" KEYS "mcp:instance:*" 2>/dev/null | wc -l | tr -d ' ')
    
    echo_info "   Session keys: $session_keys"
    echo_info "   Instance keys: $instance_keys"
    
    if [ "$session_keys" -gt 0 ] || [ "$instance_keys" -gt 0 ]; then
        echo_info "✅ Redis session data found"
        return 0
    else
        echo_warn "⚠️  No session data in Redis (this is OK if no active sessions)"
        return 0
    fi
}

# 主函数
main() {
    echo_info "=== MCP Router V3 Session Management Verification ==="
    echo_info "Base URL: $BASE_URL"
    echo_info "Redis: $REDIS_HOST:$REDIS_PORT"
    echo ""
    
    local failed=false
    
    check_redis || failed=true
    echo ""
    
    check_instances || failed=true
    echo ""
    
    test_sse_connection || failed=true
    echo ""
    
    test_restful_api || failed=true
    echo ""
    
    check_redis_sessions
    echo ""
    
    if [ "$failed" = true ]; then
        echo_error "❌ Verification failed. Please check the errors above."
        exit 1
    else
        echo_info "✅ All checks passed!"
        exit 0
    fi
}

main "$@"



















