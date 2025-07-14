#!/bin/bash
set -e

# 🚀 MCP Router 改造演示脚本
# 此脚本演示改造后的 MCP Router 系统功能

# Function to check if a port is in use
is_port_in_use() {
    lsof -i:$1 > /dev/null
    return $?
}

# Kill processes using specified ports
kill_process_on_port() {
    if is_port_in_use $1; then
        echo "Port $1 is in use. Killing the process..."
        lsof -t -i:$1 | xargs kill -9
    fi
}

# Function to wait for a service to be up
wait_for_service() {
    local port=$1
    local health_path=$2
    local service_name=$3
    local timeout=120
    local start_time=$(date +%s)

    echo -e "${YELLOW}⏳ Waiting for $service_name on port $port to be UP...${NC}"

    while true; do
        current_time=$(date +%s)
        if [ $((current_time - start_time)) -ge $timeout ]; then
            echo -e "${RED}❌ $service_name failed to start within $timeout seconds.${NC}"
            return 1
        fi

        if curl -s "http://localhost:$port$health_path" | grep -q '{"status":"UP"}'; then
            echo -e "${GREEN}✅ $service_name is UP!${NC}"
            return 0
        fi
        sleep 2
    done
}

# mcp-router, mcp-server-v1-v3, mcp-client ports
PORTS=(8050 8060 8061 8062 8070)

# Kill existing processes on the ports
for port in "${PORTS[@]}"; do
    kill_process_on_port $port
done

echo "🚀 MCP Router 改造演示"
echo "========================"

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查环境
echo -e "${BLUE}📋 检查环境...${NC}"
# 1. Java Version
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java 未安装，请安装 Java 17+${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt "17" ]; then
    echo -e "${RED}❌ 需要 Java 17 或更高版本，当前版本: $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java 版本检查通过: $(java -version 2>&1 | head -1)${NC}"

# 2. Nacos Connectivity
# echo -e "${BLUE}📡 正在验证 Nacos 连通性 (localhost:8848)...${NC}"
# Note: The user-provided command with Authorization header is not used here,
# as Spring Cloud Alibaba uses username/password from application.yml.
# A simple check for the Nacos UI endpoint is sufficient.
# if curl -X GET 'http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10' -H 'Authorization: Key-Value nacos:nacos' | grep -q "Nacos"; then
#     echo -e "${GREEN}✅ Nacos 连接成功。${NC}"
# else
#     echo -e "${RED}❌ 无法连接到 Nacos。请确保 Nacos 正在 Docker 中运行，并且端口 8848 已正确映射。${NC}"
#     exit 1
# fi


# 编译和打包项目
echo -e "${BLUE}🔨 编译和打包项目 (跳过测试)...${NC}"
mvn clean package -DskipTests > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 项目打包成功${NC}"
else
    echo -e "${RED}❌ 项目打包失败${NC}"
    exit 1
fi

# 启动 MCP Router
echo -e "${BLUE}🔀 启动 MCP Router (端口 8050)...${NC}"
nohup java -jar mcp-router/target/nacos-mcp-router-1.0.0.jar > logs/mcp-router-demo.log 2>&1 &
MCP_ROUTER_PID=$!
wait_for_service 8050 "/actuator/health" "MCP Router"

# 启动 MCP Server V1
echo -e "${BLUE}🖥️  启动 MCP Server V1 (端口 8060)...${NC}"
nohup java -jar mcp-server-v1/target/mcp-server-v1-1.0.0.jar > logs/mcp-server-v1-demo.log 2>&1 &
SERVER_V1_PID=$!
wait_for_service 8060 "/actuator/health" "MCP Server V1"

# 启动 MCP Server V2
echo -e "${BLUE}🖥️  启动 MCP Server V2 (端口 8061)...${NC}"
nohup java -jar mcp-server-v2/target/mcp-server-v2-1.0.0.jar > logs/mcp-server-v2-demo.log 2>&1 &
SERVER_V2_PID=$!
wait_for_service 8061 "/actuator/health" "MCP Server V2"

# 启动 MCP Server V3
echo -e "${BLUE}🖥️  启动 MCP Server V3 (端口 8062)...${NC}"
nohup java -jar mcp-server-v3/target/mcp-server-v3-1.0.0.jar > logs/mcp-server-v3-demo.log 2>&1 &
SERVER_V3_PID=$!
wait_for_service 8062 "/actuator/health" "MCP Server V3"

# 启动 MCP Client
echo -e "${BLUE}🧑‍💻 启动 MCP Client (端口 8070)...${NC}"
nohup java -jar mcp-client/target/mcp-client-1.0.0.jar > logs/mcp-client-demo.log 2>&1 &
CLIENT_PID=$!
wait_for_service 8070 "/actuator/health" "MCP Client"

echo ""
echo -e "${GREEN}🚀 所有服务启动完成！${NC}"
echo "========================"

# 等待 server 完成向 router 注册
echo -e "${YELLOW}⏳ 等待 MCP Server V1 V2 V3 向 Router 注册并发现工具...${NC}"
sleep 5

# 演示 MCP 协议功能
echo -e "${BLUE}📡 演示 MCP JSON-RPC 协议 (通过 Client 调用)...${NC}"
echo ""

# 1. 列出所有工具
echo -e "${YELLOW}1️⃣  测试工具列表 (GET http://localhost:8070/mcp-client/api/v1/tools/list)...${NC}"
TOOLS_RESPONSE=$(curl -s -X GET http://localhost:8070/mcp-client/api/v1/tools/list)
echo "Raw tools response: $TOOLS_RESPONSE"

# 检查是否成功获取工具列表
if echo "$TOOLS_RESPONSE" | jq -e '.success' > /dev/null && [ "$(echo "$TOOLS_RESPONSE" | jq '.success')" = "true" ]; then
    echo -e "${GREEN}✅ 成功获取工具列表${NC}"
    echo "$TOOLS_RESPONSE" | jq -r '.servers | to_entries[] | "Server: \(.key)", (.value[] | "   - \(.)")'
else
    echo -e "${RED}❌ 获取工具列表失败${NC}"
    echo "Response: $TOOLS_RESPONSE"
    echo "Client Log:"
    tail -n 20 logs/mcp-client-demo.log
fi

echo ""

# 2. 调用 PersonQueryTools 中的工具
echo -e "${YELLOW}2️⃣  测试调用 PersonQueryTools: getAllPersons...${NC}"
TOOL_RESPONSE=$(curl -s -X POST http://localhost:8070/mcp-client/api/v1/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "getAllPersons",
    "arguments": {}
  }')

if echo "$TOOL_RESPONSE" | jq -e '.success' > /dev/null && [ "$(echo "$TOOL_RESPONSE" | jq '.success')" = "true" ]; then
    echo -e "${GREEN}✅ 工具 'getAllPersons' 调用成功${NC}"
    # H2 DB is empty initially, so no data will be found. This is expected.
    echo "   数据库中初始用户数量为 0 (预期为空)"
else
    echo -e "${RED}❌ 工具 'getAllPersons' 调用失败${NC}"
    echo "Response: $TOOL_RESPONSE"
fi

echo ""

# 3. 调用 PersonModifyTools 中的工具
echo -e "${YELLOW}3️⃣  测试调用 PersonModifyTools: addPerson...${NC}"
ADD_RESPONSE=$(curl -s -X POST http://localhost:8070/mcp-client/api/v1/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "addPerson",
    "arguments": {
        "firstName": "John",
        "lastName": "Doe",
        "age": 30,
        "nationality": "American",
        "gender": "MALE"
    }
  }')

if echo "$ADD_RESPONSE" | jq -e '.success' > /dev/null && [ "$(echo "$ADD_RESPONSE" | jq '.success')" = "true" ]; then
    echo -e "${GREEN}✅ 工具 'addPerson' 调用成功${NC}"
    echo "   成功添加新用户: John Doe"
else
    echo -e "${RED}❌ 工具 'addPerson' 调用失败${NC}"
    echo "Response: $ADD_RESPONSE"
fi
echo ""

# 4. 再次调用 getAllPersons 验证
echo -e "${YELLOW}4️⃣  再次调用 getAllPersons 验证新用户...${NC}"
VERIFY_RESPONSE=$(curl -s -X POST http://localhost:8070/mcp-client/api/v1/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "getAllPersons",
    "arguments": {}
  }')

if echo "$VERIFY_RESPONSE" | jq -e '.success' > /dev/null && [ "$(echo "$VERIFY_RESPONSE" | jq '.success')" = "true" ]; then
    echo -e "${GREEN}✅ 验证成功，成功调用 getAllPersons${NC}"
    echo "   用户数据已更新"
else
    echo -e "${RED}❌ 验证失败，getAllPersons 调用失败${NC}"
    echo "Response: $VERIFY_RESPONSE"
fi


echo ""

# 显示日志信息
echo -e "${BLUE}📋 系统状态信息${NC}"
echo "========================"
echo -e "MCP Router PID: ${GREEN}$MCP_ROUTER_PID${NC}"
echo -e "MCP Server V2 PID: ${GREEN}$SERVER_V2_PID${NC}"
echo -e "MCP Client PID: ${GREEN}$CLIENT_PID${NC}"
echo -e "MCP Router URL: ${GREEN}http://localhost:8050${NC}"
echo -e "MCP Server V1 URL: ${GREEN}http://localhost:8060/mcp-server-v1${NC}"
echo -e "MCP Server V2 URL: ${GREEN}http://localhost:8061/mcp-server-v2${NC}"
echo -e "MCP Server V3 URL: ${GREEN}http://localhost:8062/mcp-server-v3${NC}"
echo -e "MCP Client URL: ${GREEN}http://localhost:8070/mcp-client${NC}"
echo -e "Client API Endpoint: ${GREEN}http://localhost:8070/mcp-client/api/v1/tools/list${NC}"
echo ""

# 提供交互选项
echo -e "${BLUE}🎮 交互选项${NC}"
echo "========================"
echo "1. 查看 MCP Router 日志: tail -f logs/mcp-router-demo.log"
echo "2. 查看 MCP Server V1 日志: tail -f logs/mcp-server-v1-demo.log"
echo "3. 查看 MCP Server V2 日志: tail -f logs/mcp-server-v2-demo.log"
echo "4. 查看 MCP Server V3 日志: tail -f logs/mcp-server-v3-demo.log"
echo "5. 查看 MCP Client 日志: tail -f logs/mcp-client-demo.log"
echo "6. 停止演示系统:"
echo "   kill $MCP_ROUTER_PID $SERVER_V2_PID $CLIENT_PID"
echo ""

echo -e "${GREEN}🎉 MCP Router 改造演示完成！${NC}"
echo ""
echo -e "${YELLOW}💡 提示:${NC}"
echo "- 整个系统 (Router, Server, Client) 均已启动."
echo "- Server V2 自动向 Router 注册了它的工具."
echo "- Client 通过 Router 发现了这些工具并成功调用."
echo "- 这展示了一个完整的 MCP 服务发现和远程工具调用流程."
echo ""
echo -e "${BLUE}📚 详细信息请查看: README.md 和 ROUTER_DESIGN.md${NC}" 