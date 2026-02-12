#!/bin/bash

# 停止所有项目脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BASE_DIR="/Users/shine/projects.mcp-router-sse-parent"
LOG_DIR="$BASE_DIR/logs"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  停止所有项目${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 项目列表
PROJECT_NAMES=("zkInfo" "demo-provider" "mcp-router-v3")
PROJECT_PORTS=("9091" "8083" "8052")

# 停止项目函数
stop_project() {
    local name=$1
    local port=$2
    
    echo -e "${YELLOW}[停止] $name${NC}"
    
    # 通过PID文件停止
    if [ -f "$LOG_DIR/${name}.pid" ]; then
        local pid=$(cat "$LOG_DIR/${name}.pid" 2>/dev/null || echo "")
        if [ ! -z "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            kill "$pid" 2>/dev/null || true
            echo -e "${GREEN}  ✅ 已停止 (PID: $pid)${NC}"
            rm -f "$LOG_DIR/${name}.pid"
        fi
    fi
    
    # 通过端口停止
    local port_pid=$(lsof -ti:$port 2>/dev/null || echo "")
    if [ ! -z "$port_pid" ]; then
        kill "$port_pid" 2>/dev/null || true
        echo -e "${GREEN}  ✅ 已停止端口 $port 上的进程${NC}"
    fi
    
    # 通过进程名停止
    pkill -f "$name" 2>/dev/null || true
}

# 停止所有项目
for i in "${!PROJECT_NAMES[@]}"; do
    name="${PROJECT_NAMES[$i]}"
    port="${PROJECT_PORTS[$i]}"
    stop_project "$name" "$port"
    echo ""
done

# 等待进程结束
sleep 2

# 验证停止状态
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  停止状态检查${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

for i in "${!PROJECT_NAMES[@]}"; do
    name="${PROJECT_NAMES[$i]}"
    port="${PROJECT_PORTS[$i]}"
    if lsof -ti:$port > /dev/null 2>&1; then
        echo -e "${RED}❌ $name - 端口 $port 仍在运行${NC}"
    else
        echo -e "${GREEN}✅ $name - 已停止${NC}"
    fi
done

echo ""
echo -e "${GREEN}所有项目已停止！${NC}"
echo ""

