#!/bin/bash

# 启动所有项目脚本

set -e


# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BASE_DIR="/Users/shine/projects.mcp-router-sse-parent"
LOG_DIR="$BASE_DIR/logs"
mkdir -p "$LOG_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  启动所有项目${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查Java和Maven
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java未安装${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven未安装${NC}"
    exit 1
fi

# 项目列表和配置 (项目路径:HTTP端口:主类名)
# 使用数组存储项目信息
PROJECT_NAMES=("zkInfo" "demo-provider" "mcp-router-v3")
PROJECT_PATHS=("zk-mcp-parent/zkInfo" "zk-mcp-parent/demo-provider" "mcp-router-v3")
PROJECT_PORTS=("9091" "8083" "8052")
PROJECT_MAIN_CLASSES=("ZkInfoApplication" "DemoProviderApplication" "McpRouterV3Application")

# 启动项目函数
start_project() {
    local name=$1
    local path=$2
    local port=$3
    local main_class=$4
    
    echo -e "${YELLOW}[启动] $name (端口: $port)${NC}"
    
    cd "$BASE_DIR/$path"
    
    # 检查是否已经在运行
    if lsof -ti:$port > /dev/null 2>&1; then
        echo -e "${YELLOW}  ⚠️  端口 $port 已被占用，跳过启动${NC}"
        return 0
    fi
    
    # 启动项目
    nohup mvn spring-boot:run > "$LOG_DIR/${name}.log" 2>&1 &
    local pid=$!
    
    echo "$pid" > "$LOG_DIR/${name}.pid"
    echo -e "${GREEN}  ✅ 已启动 (PID: $pid)${NC}"
    echo -e "${BLUE}     日志: $LOG_DIR/${name}.log${NC}"
    
    # 等待启动
    sleep 3
}

# 启动所有项目
echo -e "${BLUE}开始启动项目...${NC}"
echo ""

for i in "${!PROJECT_NAMES[@]}"; do
    name="${PROJECT_NAMES[$i]}"
    path="${PROJECT_PATHS[$i]}"
    port="${PROJECT_PORTS[$i]}"
    main_class="${PROJECT_MAIN_CLASSES[$i]}"
    start_project "$name" "$path" "$port" "$main_class"
    echo ""
done

# 等待所有服务启动
echo -e "${YELLOW}等待服务启动...${NC}"
sleep 10

# 验证服务状态
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  服务状态检查${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

for i in "${!PROJECT_NAMES[@]}"; do
    name="${PROJECT_NAMES[$i]}"
    port="${PROJECT_PORTS[$i]}"
    
    if lsof -ti:$port > /dev/null 2>&1; then
        echo -e "${GREEN}✅ $name - 端口 $port 运行中${NC}"
        
        # 尝试健康检查
        if [ "$name" == "zkInfo" ]; then
            if curl -s "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
                echo -e "   ${GREEN}健康检查: 正常${NC}"
            fi
        elif [ "$name" == "mcp-router-v3" ]; then
            if curl -s "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
                echo -e "   ${GREEN}健康检查: 正常${NC}"
            fi
        fi
    else
        echo -e "${RED}❌ $name - 端口 $port 未运行${NC}"
        echo -e "   ${YELLOW}请检查日志: $LOG_DIR/${name}.log${NC}"
    fi
    echo ""
done

echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}  启动完成！${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "服务列表:"
for i in "${!PROJECT_NAMES[@]}"; do
    name="${PROJECT_NAMES[$i]}"
    port="${PROJECT_PORTS[$i]}"
    echo -e "  - ${GREEN}$name${NC}: http://localhost:$port"
done
echo ""
echo -e "停止所有服务: ${YELLOW}bash stop-all-projects.sh${NC}"
echo -e "查看日志: ${YELLOW}tail -f $LOG_DIR/*.log${NC}"
echo ""

