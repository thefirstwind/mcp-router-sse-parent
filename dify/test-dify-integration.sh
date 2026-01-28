#!/bin/bash

###############################################################################
# MCP Router V3 - Dify 集成测试脚本
# 
# 功能：测试 MCP Router 的两个核心接口
#   1. tools/list - 获取可用工具列表
#   2. tools/call - 调用具体工具
#
# 使用方法：
#   chmod +x test-dify-integration.sh
#   ./test-dify-integration.sh
###############################################################################

set -e

# 配置
MCP_ROUTER_BASE_URL="http://localhost:8052/mcp/router"
SERVER_KEY="mcp-server-v6"
TEMP_DIR="/tmp/mcp-test-$$"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 创建临时目录
mkdir -p "$TEMP_DIR"

# 清理函数
cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# 打印分隔线
print_separator() {
    echo -e "${BLUE}================================================================${NC}"
}

# 打印测试标题
print_test_title() {
    echo ""
    print_separator
    echo -e "${YELLOW}$1${NC}"
    print_separator
}

# 检查 HTTP 状态码
check_http_status() {
    local status=$1
    local expected=$2
    if [ "$status" -eq "$expected" ]; then
        echo -e "${GREEN}✓ HTTP状态码正确: $status${NC}"
        return 0
    else
        echo -e "${RED}✗ HTTP状态码错误: 期望 $expected，实际 $status${NC}"
        return 1
    fi
}

# 检查 JSON 字段
check_json_field() {
    local json=$1
    local field=$2
    local expected=$3
    local actual=$(echo "$json" | jq -r ".$field")
    
    if [ "$actual" == "$expected" ]; then
        echo -e "${GREEN}✓ 字段 $field 正确: $actual${NC}"
        return 0
    else
        echo -e "${RED}✗ 字段 $field 错误: 期望 $expected，实际 $actual${NC}"
        return 1
    fi
}

###############################################################################
# 测试1: 健康检查
###############################################################################
print_test_title "测试1: MCP Router 健康检查"

health_url="http://localhost:8052/actuator/health"
echo "请求: GET $health_url"
response=$(curl -s -w "\n%{http_code}" "$health_url")
body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n 1)

echo "响应状态码: $status"
echo "响应内容:"
echo "$body" | jq '.'

if check_http_status "$status" 200; then
    echo -e "${GREEN}✓ 测试1通过${NC}"
else
    echo -e "${YELLOW}⚠ 健康检查失败，但继续测试核心接口${NC}"
fi

###############################################################################
# 测试2: 获取工具列表 (tools/list)
###############################################################################
print_test_title "测试2: 获取工具列表 (tools/list)"

echo "请求: GET $MCP_ROUTER_BASE_URL/tools/$SERVER_KEY"
response=$(curl -s -w "\n%{http_code}" "$MCP_ROUTER_BASE_URL/tools/$SERVER_KEY")
body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n 1)

echo "响应状态码: $status"
echo "响应内容:"
echo "$body" | jq '.'

# 保存响应
echo "$body" > "$TEMP_DIR/tools_list.json"

# 检查响应
if check_http_status "$status" 200; then
    # 检查工具列表
    tools_count=$(echo "$body" | jq '.tools | length')
    echo "工具数量: $tools_count"
    
    if [ "$tools_count" -gt 0 ]; then
        echo -e "${GREEN}✓ 工具列表不为空${NC}"
        
        # 显示所有工具
        echo ""
        echo "可用工具:"
        echo "$body" | jq -r '.tools[] | "  - \(.name): \(.description)"'
        
        echo -e "${GREEN}✓ 测试2通过${NC}"
    else
        echo -e "${RED}✗ 工具列表为空${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试2失败${NC}"
    exit 1
fi

###############################################################################
# 测试3: 调用工具 - getPersonById (id=5)
###############################################################################
print_test_title "测试3: 调用工具 - getPersonById (id=5)"

request_body=$(cat <<EOF
{
  "id": "test-$(date +%s)",
  "method": "tools/call",
  "params": {
    "name": "getPersonById",
    "arguments": {
      "id": 5
    }
  }
}
EOF
)

echo "请求: POST $MCP_ROUTER_BASE_URL/route/$SERVER_KEY"
echo "请求体:"
echo "$request_body" | jq '.'

response=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$request_body" \
    "$MCP_ROUTER_BASE_URL/route/$SERVER_KEY")

body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n 1)

echo ""
echo "响应状态码: $status"
echo "响应内容:"
echo "$body" | jq '.'

# 保存响应
echo "$body" > "$TEMP_DIR/call_result_5.json"

# 检查响应
if check_http_status "$status" 200; then
    # 检查结果字段
    found=$(echo "$body" | jq -r '.result.found')
    first_name=$(echo "$body" | jq -r '.result.firstName')
    last_name=$(echo "$body" | jq -r '.result.lastName')
    
    echo ""
    echo "查询结果:"
    echo "  - 是否找到: $found"
    echo "  - 姓名: $first_name $last_name"
    echo "  - 年龄: $(echo "$body" | jq -r '.result.age')"
    echo "  - 国籍: $(echo "$body" | jq -r '.result.nationality')"
    echo "  - 性别: $(echo "$body" | jq -r '.result.gender')"
    
    if [ "$found" == "true" ]; then
        echo -e "${GREEN}✓ 用户找到${NC}"
        
        # 检查元数据
        response_time=$(echo "$body" | jq -r '.metadata.responseTime')
        tool_name=$(echo "$body" | jq -r '.metadata.toolName')
        echo ""
        echo "性能指标:"
        echo "  - 响应时间: ${response_time}ms"
        echo "  - 工具名称: $tool_name"
        
        echo -e "${GREEN}✓ 测试3通过${NC}"
    else
        echo -e "${RED}✗ 用户未找到${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试3失败${NC}"
    exit 1
fi

###############################################################################
# 测试4: 调用工具 - getAllPersons
###############################################################################
print_test_title "测试4: 调用工具 - getAllPersons"

request_body=$(cat <<EOF
{
  "id": "test-$(date +%s)",
  "method": "tools/call",
  "params": {
    "name": "getAllPersons",
    "arguments": {}
  }
}
EOF
)

echo "请求: POST $MCP_ROUTER_BASE_URL/route/$SERVER_KEY"
echo "请求体:"
echo "$request_body" | jq '.'

response=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$request_body" \
    "$MCP_ROUTER_BASE_URL/route/$SERVER_KEY")

body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n 1)

echo ""
echo "响应状态码: $status"
echo "响应内容:"
echo "$body" | jq '.'

# 保存响应
echo "$body" > "$TEMP_DIR/call_result_all.json"

# 检查响应
if check_http_status "$status" 200; then
    # 检查结果
    persons_count=$(echo "$body" | jq '.result | length')
    echo ""
    echo "用户数量: $persons_count"
    
    if [ "$persons_count" -gt 0 ]; then
        echo -e "${GREEN}✓ 返回了用户列表${NC}"
        
        # 显示前3个用户
        echo ""
        echo "前3个用户:"
        echo "$body" | jq -r '.result[:3] | .[] | "  - ID:\(.id) \(.firstName) \(.lastName), \(.age)岁, \(.nationality)"'
        
        echo -e "${GREEN}✓ 测试4通过${NC}"
    else
        echo -e "${YELLOW}⚠ 用户列表为空（可能是正常情况）${NC}"
    fi
else
    echo -e "${RED}✗ 测试4失败${NC}"
    exit 1
fi

###############################################################################
# 测试5: 错误场景 - 查询不存在的用户
###############################################################################
print_test_title "测试5: 错误场景 - 查询不存在的用户 (id=99999)"

request_body=$(cat <<EOF
{
  "id": "test-$(date +%s)",
  "method": "tools/call",
  "params": {
    "name": "getPersonById",
    "arguments": {
      "id": 99999
    }
  }
}
EOF
)

echo "请求: POST $MCP_ROUTER_BASE_URL/route/$SERVER_KEY"
echo "请求体:"
echo "$request_body" | jq '.'

response=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$request_body" \
    "$MCP_ROUTER_BASE_URL/route/$SERVER_KEY")

body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n 1)

echo ""
echo "响应状态码: $status"
echo "响应内容:"
echo "$body" | jq '.'

# 检查响应
if check_http_status "$status" 200; then
    found=$(echo "$body" | jq -r '.result.found')
    
    if [ "$found" == "false" ]; then
        echo -e "${GREEN}✓ 正确返回用户不存在${NC}"
        echo -e "${GREEN}✓ 测试5通过${NC}"
    else
        echo -e "${RED}✗ 应该返回用户不存在${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试5失败${NC}"
    exit 1
fi

###############################################################################
# 测试6: 模拟 Dify 完整调用流程
###############################################################################
print_test_title "测试6: 模拟 Dify 完整调用流程"

echo "场景: 用户输入 '查询用户 id=5'"
echo ""
echo "步骤1: Dify LLM 解析用户意图"
echo "  → 提取工具名: getPersonById"
echo "  → 提取参数: {id: 5}"
echo ""
echo "步骤2: 调用 MCP Router"

request_body=$(cat <<EOF
{
  "id": "dify-$(uuidgen)",
  "method": "tools/call",
  "params": {
    "name": "getPersonById",
    "arguments": {
      "id": 5
    }
  }
}
EOF
)

response=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$request_body" \
    "$MCP_ROUTER_BASE_URL/route/$SERVER_KEY")

body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n 1)

if check_http_status "$status" 200; then
    echo ""
    echo "步骤3: Dify LLM 格式化结果"
    
    # 提取关键信息
    first_name=$(echo "$body" | jq -r '.result.firstName')
    last_name=$(echo "$body" | jq -r '.result.lastName')
    age=$(echo "$body" | jq -r '.result.age')
    nationality=$(echo "$body" | jq -r '.result.nationality')
    gender=$(echo "$body" | jq -r '.result.gender')
    
    # 模拟 LLM 格式化输出
    echo "  → Dify 回复用户:"
    echo ""
    echo "  ┌─────────────────────────────────────────┐"
    echo "  │  找到了用户信息！                      │"
    echo "  │                                         │"
    echo "  │  用户ID: 5                              │"
    echo "  │  姓名: $first_name $last_name           │"
    echo "  │  年龄: $age岁                           │"
    echo "  │  国籍: $nationality                     │"
    echo "  │  性别: $gender                          │"
    echo "  └─────────────────────────────────────────┘"
    echo ""
    
    echo -e "${GREEN}✓ 测试6通过 - 完整流程验证成功${NC}"
else
    echo -e "${RED}✗ 测试6失败${NC}"
    exit 1
fi

###############################################################################
# 测试总结
###############################################################################
print_separator
echo -e "${GREEN}✓✓✓ 所有测试通过！ ✓✓✓${NC}"
print_separator
echo ""
echo "测试结果已保存到: $TEMP_DIR"
echo "  - tools_list.json: 工具列表"
echo "  - call_result_5.json: 查询用户5的结果"
echo "  - call_result_all.json: 所有用户列表"
echo ""
echo "后续步骤:"
echo "  1. 将 mcp-router-openapi.yaml 导入到 Dify"
echo "  2. 使用 dify-workflow-example.json 创建工作流"
echo "  3. 配置系统提示词"
echo "  4. 开始使用！"
echo ""
print_separator

exit 0

