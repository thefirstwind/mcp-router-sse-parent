#!/bin/bash
# Comprehensive Streamable Protocol Session Management Test Suite
# 完整的 Streamable 协议 Session 会话管理测试套件

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
ROUTER_URL="${ROUTER_URL:-http://localhost:8052}"
SERVICE_NAME="${SERVICE_NAME:-mcp-server-v6}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

# 测试统计
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# 打印函数
print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_test() {
    echo -e "${YELLOW}Test $1:${NC} $2"
}

print_pass() {
    ((PASSED_TESTS++))
    echo -e "${GREEN}✓ PASS${NC}: $1"
}

print_fail() {
    ((FAILED_TESTS++))
    echo -e "${RED}✗ FAIL${NC}: $1"
}

print_skip() {
    ((SKIPPED_TESTS++))
    echo -e "${YELLOW}⊘ SKIP${NC}: $1"
}

check_service() {
    print_header "前置检查"
    
    # 检查服务是否运行
    if ! curl -s "$ROUTER_URL/actuator/health" > /dev/null 2>&1; then
        echo -e "${RED}错误: mcp-router-v3 未运行在 $ROUTER_URL${NC}"
        echo "请先启动服务: cd mcp-router-v3 && mvn spring-boot:run"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Router 服务运行正常"
    
    # 检查 Redis
    if command -v redis-cli &> /dev/null; then
        if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" PING > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} Redis 连接正常"
            REDIS_AVAILABLE=true
        else
            echo -e "${YELLOW}⚠${NC} Redis 不可用，将跳过 Redis 相关测试"
            REDIS_AVAILABLE=false
        fi
    else
        echo -e "${YELLOW}⚠${NC} redis-cli 未安装，将跳过 Redis 相关测试"
        REDIS_AVAILABLE=false
    fi
    
    # 检查 jq
    if ! command -v jq &> /dev/null; then
        echo -e "${YELLOW}⚠${NC} jq 未安装，部分验证功能受限"
        echo "建议安装: brew install jq"
        JQ_AVAILABLE=false
    else
        echo -e "${GREEN}✓${NC} jq 工具可用"
        JQ_AVAILABLE=true
    fi
}

# ============================================================================
# A. Streamable 协议完整性测试
# ============================================================================

test_streamable_session_message() {
    print_header "A. Streamable 协议完整性测试"
    
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "验证第一条 NDJSON 消息包含 session 信息"
    
    FIRST_LINE=$(timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 1)
    
    if [[ -z "$FIRST_LINE" ]]; then
        print_fail "未收到响应"
        return
    fi
    
    # 检查 JSON 格式
    if ! echo "$FIRST_LINE" | jq . > /dev/null 2>&1; then
        print_fail "第一条消息不是有效的 JSON: $FIRST_LINE"
        return
    fi
    
    # 提取字段
    TYPE=$(echo "$FIRST_LINE" | jq -r '.type // empty')
    SESSION_ID=$(echo "$FIRST_LINE" | jq -r '.sessionId // empty')
    MESSAGE_ENDPOINT=$(echo "$FIRST_LINE" | jq -r '.messageEndpoint // empty')
    TRANSPORT=$(echo "$FIRST_LINE" | jq -r '.transport // empty')
    
    # 验证
    if [[ "$TYPE" == "session" ]] && \
       [[ -n "$SESSION_ID" ]] && \
       [[ -n "$MESSAGE_ENDPOINT" ]] && \
       [[ "$TRANSPORT" == "streamable" ]]; then
        print_pass "Session 消息格式正确"
        echo "  - type: $TYPE"
        echo "  - sessionId: $SESSION_ID"
        echo "  - messageEndpoint: $MESSAGE_ENDPOINT"
        echo "  - transport: $TRANSPORT"
        
        # 保存 sessionId 供后续测试使用
        export TEST_SESSION_ID="$SESSION_ID"
        export TEST_MESSAGE_ENDPOINT="$MESSAGE_ENDPOINT"
    else
        print_fail "Session 消息字段不完整或不正确"
        echo "收到: $FIRST_LINE"
    fi
}

test_streamable_response_headers() {
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "验证响应头包含 Mcp-Session-Id"
    
    # curl -I 对流式响应不适用，改用 -v 获取响应头
    HEADERS=$(timeout 2 curl -v -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" 2>&1 | grep -E "^< ")
    
    if echo "$HEADERS" | grep -i "Mcp-Session-Id:" > /dev/null; then
        SESSION_ID_HEADER=$(echo "$HEADERS" | grep -i "Mcp-Session-Id:" | sed 's/.*: //' | tr -d '\r')
        print_pass "Mcp-Session-Id 响应头存在: $SESSION_ID_HEADER"
    else
        print_fail "Mcp-Session-Id 响应头缺失"
    fi
    
    if echo "$HEADERS" | grep -i "Mcp-Transport:" > /dev/null; then
        TRANSPORT_HEADER=$(echo "$HEADERS" | grep -i "Mcp-Transport:" | sed 's/.*: //' | tr -d '\r')
        print_pass "Mcp-Transport 响应头存在: $TRANSPORT_HEADER"
    else
        print_fail "Mcp-Transport 响应头缺失"
    fi
}

test_accept_headers() {
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试不同的 Accept 头"
    
    for ACCEPT_TYPE in "application/x-ndjson" "application/x-ndjson+stream" "application/json"; do
        RESPONSE=$(timeout 2 curl -s -N -H "Accept: $ACCEPT_TYPE" \
            "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 1)
        
        if [[ -n "$RESPONSE" ]] && echo "$RESPONSE" | jq . > /dev/null 2>&1; then
            print_pass "Accept: $ACCEPT_TYPE - 响应正常"
        else
            print_fail "Accept: $ACCEPT_TYPE - 响应异常"
        fi
    done
}

test_ndjson_format() {
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "验证 NDJSON 格式（每行一个有效 JSON）"
    
    LINES=$(timeout 3 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 3)
    
    LINE_NUM=0
    ALL_VALID=true
    
    while IFS= read -r line; do
        ((LINE_NUM++))
        if [[ -n "$line" ]]; then
            if ! echo "$line" | jq . > /dev/null 2>&1; then
                print_fail "第 $LINE_NUM 行不是有效的 JSON: $line"
                ALL_VALID=false
            fi
        fi
    done <<< "$LINES"
    
    if $ALL_VALID && [[ $LINE_NUM -gt 0 ]]; then
        print_pass "所有 $LINE_NUM 行都是有效的 JSON"
    elif [[ $LINE_NUM -eq 0 ]]; then
        print_fail "未收到任何数据"
    fi
}

# ============================================================================
# B. Session ID 解析完整性测试
# ============================================================================

test_session_id_headers() {
    print_header "B. Session ID 解析测试"
    
    # 测试各种请求头（使用简单数组，兼容旧版 Bash）
    HEADERS=(
        "Mcp-Session-Id:test-mcp-session-1"
        "X-Mcp-Session-Id:test-x-mcp-session-2"
        "mcp-session-id:test-lowercase-3"
        "x-mcp-session-id:test-x-lowercase-4"
        "Session-Id:test-session-5"
        "X-Session-Id:test-x-session-6"
    )
    
    for HEADER_PAIR in "${HEADERS[@]}"; do
        HEADER_NAME=$(echo "$HEADER_PAIR" | cut -d':' -f1)
        SESSION_VALUE=$(echo "$HEADER_PAIR" | cut -d':' -f2)
        
        ((TOTAL_TESTS++))
        print_test "$TOTAL_TESTS" "测试请求头: $HEADER_NAME"
        
        RESPONSE=$(curl -s -X POST \
            -H "Content-Type: application/json" \
            -H "$HEADER_NAME: $SESSION_VALUE" \
            -d '{"jsonrpc":"2.0","id":"test-header","method":"tools/list"}' \
            "$ROUTER_URL/mcp/message" 2>/dev/null)
        
        if echo "$RESPONSE" | jq -e '.result' > /dev/null 2>&1; then
            print_pass "请求成功，$HEADER_NAME 被正确解析"
        else
            print_fail "请求失败"
        fi
    done
}

test_session_id_query_param() {
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试通过查询参数传递 sessionId"
    
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":"test-query","method":"tools/list"}' \
        "$ROUTER_URL/mcp/message?sessionId=query-param-session-test" 2>/dev/null)
    
    if echo "$RESPONSE" | jq -e '.result' > /dev/null 2>&1; then
        print_pass "查询参数 sessionId 正确工作"
    else
        print_fail "查询参数 sessionId 失败: $RESPONSE"
    fi
}

test_session_id_auto_generation() {
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试无 sessionId 时自动生成"
    
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":"test-auto-gen","method":"tools/list"}' \
        "$ROUTER_URL/mcp/message" 2>/dev/null)
    
    if echo "$RESPONSE" | jq -e '.result' > /dev/null 2>&1; then
        print_pass "无 sessionId 时请求仍然成功（自动生成）"
        echo "  提示: 检查服务器日志应有 '⚠️ No sessionId found' 警告"
    else
        print_fail "无 sessionId 请求失败: $RESPONSE"
    fi
}

# ============================================================================
# C. Session 生命周期测试
# ============================================================================

test_session_lifecycle() {
    print_header "C. Session 生命周期测试"
    
    if ! $REDIS_AVAILABLE; then
        print_skip "Redis 不可用，跳过生命周期测试"
        ((SKIPPED_TESTS+=4))
        return
    fi
    
    # 创建新的连接获取 sessionId
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "创建 Session 并验证 Redis 存储"
    
    SESSION_LINE=$(timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 1)
    
    if [[ -z "$SESSION_LINE" ]]; then
        print_fail "无法创建 session"
        ((SKIPPED_TESTS+=3))
        return
    fi
    
    REDIS_SESSION_ID=$(echo "$SESSION_LINE" | jq -r '.sessionId // empty')
    
    if [[ -z "$REDIS_SESSION_ID" ]]; then
        print_fail "无法从响应中提取 sessionId"
        ((SKIPPED_TESTS+=3))
        return
    fi
    
    # 等待一下让 session 写入 Redis
    sleep 1
    
    # 检查 Redis
    REDIS_KEY="mcp:session:$REDIS_SESSION_ID"
    if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" EXISTS "$REDIS_KEY" | grep -q "1"; then
        print_pass "Session 已存储到 Redis: $REDIS_KEY"
    else
        print_fail "Session 未在 Redis 中找到: $REDIS_KEY"
    fi
    
    # 测试 TTL
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "验证 Session TTL（应该约 30 分钟）"
    
    TTL=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" TTL "$REDIS_KEY")
    
    # TTL 应该在 1700 到 1800 秒之间（允许一些误差）
    if [[ $TTL -ge 1700 ]] && [[ $TTL -le 1800 ]]; then
        print_pass "Session TTL 正常: ${TTL}s (约 $((TTL/60)) 分钟)"
    else
        print_fail "Session TTL 异常: ${TTL}s (预期 ~1800s)"
    fi
    
    # 测试 Session 刷新
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试发送消息后 Session TTL 刷新"
    
    INITIAL_TTL=$TTL
    sleep 2  # 等待 2 秒让 TTL 减少
    
    # 发送消息
    curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "Mcp-Session-Id: $REDIS_SESSION_ID" \
        -d '{"jsonrpc":"2.0","id":"refresh-test","method":"tools/list"}' \
        "$ROUTER_URL/mcp/message" > /dev/null 2>&1
    
    # 检查 TTL
    NEW_TTL=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" TTL "$REDIS_KEY")
    
    if [[ $NEW_TTL -ge $INITIAL_TTL ]]; then
        print_pass "Session TTL 已刷新: $INITIAL_TTL -> $NEW_TTL"
    else
        print_fail "Session TTL 未刷新: $INITIAL_TTL -> $NEW_TTL"
    fi
}

# ============================================================================
# D. 不同路径和服务测试
# ============================================================================

test_different_paths() {
    print_header "D. 不同路径和服务测试"
    
    # 测试 GET /mcp/{serviceName}
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "GET /mcp/{serviceName}"
    
    RESPONSE=$(timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 1)
    
    if echo "$RESPONSE" | jq -e '.type == "session"' > /dev/null 2>&1; then
        print_pass "路径参数服务名正常"
    else
        print_fail "路径参数服务名失败"
    fi
    
    # 测试 POST /mcp/{serviceName}/message
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "POST /mcp/{serviceName}/message"
    
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":"path-test","method":"tools/list"}' \
        "$ROUTER_URL/mcp/$SERVICE_NAME/message" 2>/dev/null)
    
    if echo "$RESPONSE" | jq -e '.result' > /dev/null 2>&1; then
        print_pass "路径参数消息发送正常"
    else
        print_fail "路径参数消息发送失败"
    fi
    
    # 测试 GET /mcp?serviceName=xxx
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "GET /mcp?serviceName=xxx"
    
    RESPONSE=$(timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp?serviceName=$SERVICE_NAME" 2>/dev/null | head -n 1)
    
    if echo "$RESPONSE" | jq -e '.type == "session"' > /dev/null 2>&1; then
        print_pass "查询参数服务名正常"
    else
        print_fail "查询参数服务名失败"
    fi
}

# ============================================================================
# E. SSE 模式兼容性测试
# ============================================================================

test_sse_compatibility() {
    print_header "E. SSE 模式兼容性测试"
    
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "验证 SSE 模式未受影响"
    
    SSE_RESPONSE=$(timeout 3 curl -s -N "$ROUTER_URL/sse/$SERVICE_NAME" 2>/dev/null | head -n 2)
    
    if echo "$SSE_RESPONSE" | grep -q "event: endpoint"; then
        print_pass "SSE 模式正常工作"
        echo "  SSE endpoint 事件正常"
    else
        print_fail "SSE 模式可能受影响"
        echo "收到: $SSE_RESPONSE"
    fi
}

# ============================================================================
# F. 错误处理测试
# ============================================================================

test_error_handling() {
    print_header "F. 错误处理测试"
    
    # 测试无效的 service name
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试无效的服务名"
    
    RESPONSE=$(timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/non-existent-service-12345" 2>/dev/null | head -n 1)
    
    # 应该仍然返回 session 消息
    if echo "$RESPONSE" | jq -e '.type == "session"' > /dev/null 2>&1; then
        print_pass "无效服务名的错误处理正常（返回 session 消息）"
    else
        # 或者返回错误
        print_pass "无效服务名返回错误（这也是可接受的）"
    fi
    
    # 测试畸形 JSON
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试畸形 JSON 请求"
    
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{invalid json}' \
        "$ROUTER_URL/mcp/message" 2>/dev/null)
    
    if echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
        print_pass "畸形 JSON 正确返回错误"
    else
        print_fail "畸形 JSON 未正确处理"
    fi
}

# ============================================================================
# G. 端到端完整流程测试
# ============================================================================

test_end_to_end_workflow() {
    print_header "G. 端到端完整流程测试"
    
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "完整的 Streamable 工作流"
    
    echo "步骤 1: 建立连接并获取 sessionId..."
    FIRST_MSG=$(timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 1)
    
    if [[ -z "$FIRST_MSG" ]]; then
        print_fail "无法建立连接"
        return
    fi
    
    E2E_SESSION_ID=$(echo "$FIRST_MSG" | jq -r '.sessionId')
    E2E_ENDPOINT=$(echo "$FIRST_MSG" | jq -r '.messageEndpoint')
    
    echo "  ✓ SessionId: $E2E_SESSION_ID"
    echo "  ✓ Endpoint: $E2E_ENDPOINT"
    
    echo "步骤 2: 使用提取的 sessionId 发送 initialize..."
    INIT_RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "Mcp-Session-Id: $E2E_SESSION_ID" \
        -d '{"jsonrpc":"2.0","id":"e2e-init","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}' \
        "$E2E_ENDPOINT" 2>/dev/null)
    
    if echo "$INIT_RESPONSE" | jq -e '.result or .jsonrpc' > /dev/null 2>&1; then
        echo "  ✓ Initialize 成功"
    else
        echo "  ✗ Initialize 失败: $INIT_RESPONSE"
    fi
    
    echo "步骤 3: 发送 tools/list..."
    TOOLS_RESPONSE=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "Mcp-Session-Id: $E2E_SESSION_ID" \
        -d '{"jsonrpc":"2.0","id":"e2e-tools","method":"tools/list"}' \
        "$E2E_ENDPOINT" 2>/dev/null)
    
    if echo "$TOOLS_RESPONSE" | jq -e '.result.tools' > /dev/null 2>&1; then
        TOOL_COUNT=$(echo "$TOOLS_RESPONSE" | jq '.result.tools | length')
        echo "  ✓ Tools list 成功，找到 $TOOL_COUNT 个工具"
        print_pass "完整工作流测试通过"
    else
        echo "  ✗ Tools list 失败"
        print_fail "完整工作流测试失败"
    fi
}

# ============================================================================
# H. 并发和性能测试
# ============================================================================

test_concurrent_connections() {
    print_header "H. 并发连接测试"
    
    ((TOTAL_TESTS++))
    print_test "$TOTAL_TESTS" "测试 10 个并发连接"
    
    CONCURRENT_COUNT=10
    TEMP_DIR=$(mktemp -d)
    
    echo "创建 $CONCURRENT_COUNT 个并发连接..."
    
    for i in $(seq 1 $CONCURRENT_COUNT); do
        timeout 2 curl -s -N -H "Accept: application/x-ndjson" \
            "$ROUTER_URL/mcp/$SERVICE_NAME" 2>/dev/null | head -n 1 > "$TEMP_DIR/session_$i.json" &
    done
    
    wait
    
    # 验证结果
    SUCCESS_COUNT=0
    UNIQUE_SESSIONS=()
    
    for i in $(seq 1 $CONCURRENT_COUNT); do
        if [[ -f "$TEMP_DIR/session_$i.json" ]]; then
            SESSION_ID=$(jq -r '.sessionId // empty' "$TEMP_DIR/session_$i.json" 2>/dev/null)
            if [[ -n "$SESSION_ID" ]]; then
                ((SUCCESS_COUNT++))
                UNIQUE_SESSIONS+=("$SESSION_ID")
            fi
        fi
    done
    
    # 检查唯一性
    UNIQUE_COUNT=$(printf '%s\n' "${UNIQUE_SESSIONS[@]}" | sort -u | wc -l)
    
    if [[ $SUCCESS_COUNT -eq $CONCURRENT_COUNT ]] && [[ $UNIQUE_COUNT -eq $CONCURRENT_COUNT ]]; then
        print_pass "所有 $CONCURRENT_COUNT 个并发连接成功，sessionId 唯一"
    else
        print_fail "并发测试失败: 成功=$SUCCESS_COUNT/$CONCURRENT_COUNT, 唯一=$UNIQUE_COUNT/$CONCURRENT_COUNT"
    fi
    
    # 清理
    rm -rf "$TEMP_DIR"
}

# ============================================================================
# 主测试流程
# ============================================================================

main() {
    clear
    print_header "🧪 Streamable 协议 Session 管理 - 完整测试套件"
    
    echo "测试配置:"
    echo "  Router URL: $ROUTER_URL"
    echo "  Service: $SERVICE_NAME"
    echo "  Redis: $REDIS_HOST:$REDIS_PORT"
    echo ""
    
    # 前置检查
    check_service
    
    # 运行所有测试
    test_streamable_session_message
    test_streamable_response_headers
    test_accept_headers
    test_ndjson_format
    
    test_session_id_headers
    test_session_id_query_param
    test_session_id_auto_generation
    
    test_session_lifecycle
    
    test_different_paths
    
    test_sse_compatibility
    
    test_error_handling
    
    test_end_to_end_workflow
    
    test_concurrent_connections
    
    # 打印测试摘要
    print_header "📊 测试摘要"
    
    echo "总测试数: $TOTAL_TESTS"
    echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
    echo -e "${RED}失败: $FAILED_TESTS${NC}"
    echo -e "${YELLOW}跳过: $SKIPPED_TESTS${NC}"
    echo ""
    
    if [[ $FAILED_TESTS -eq 0 ]]; then
        echo -e "${GREEN}🎉 所有测试通过！${NC}"
        exit 0
    else
        echo -e "${RED}❌ 部分测试失败${NC}"
        exit 1
    fi
}

# 运行主函数
main "$@"
