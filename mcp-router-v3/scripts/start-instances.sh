#!/bin/bash

# MCP Router V3 多实例启动脚本
# 启动 3 个实例：8051, 8052, 8053

# 不使用 set -e，因为我们需要处理错误并继续
# set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
PID_DIR="$PROJECT_DIR/pids"

# 创建日志和 PID 目录
mkdir -p "$LOG_DIR" "$PID_DIR"

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查端口是否被占用
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 1
    fi
    return 0
}

# 启动单个实例
start_instance() {
    local port=$1
    local instance_id=$2
    local log_file="$LOG_DIR/router-${port}.log"
    local pid_file="$PID_DIR/router-${port}.pid"
    
    if [ -f "$pid_file" ]; then
        local old_pid=$(cat "$pid_file")
        if ps -p "$old_pid" > /dev/null 2>&1; then
            echo_warn "Instance on port $port is already running (PID: $old_pid)"
            return 1
        else
            rm -f "$pid_file"
        fi
    fi
    
    if ! check_port $port; then
        echo_error "Port $port is already in use"
        return 1
    fi
    
    echo_info "Starting mcp-router-v3 instance on port $port (instance-id: $instance_id)..."
    
    cd "$PROJECT_DIR"
    
    # 设置环境变量
    export SERVER_PORT=$port
    export MCP_SESSION_INSTANCE_ID=$instance_id
    export SPRING_PROFILES_ACTIVE=multi-instance
    
    # 启动应用（后台运行）
    # 注意：mvn spring-boot:run 会启动 Maven 进程，我们需要找到实际的 Java 进程
    nohup mvn spring-boot:run \
        -Dspring-boot.run.arguments="--server.port=$port --mcp.session.instance-id=$instance_id" \
        > "$log_file" 2>&1 &
    
    local maven_pid=$!
    echo $maven_pid > "$pid_file"
    
    # 等待启动并查找实际的 Java 进程
    echo_info "Waiting for instance on port $port to start..."
    local max_wait=60
    local waited=0
    local java_pid=""
    
    while [ $waited -lt $max_wait ]; do
        # 检查端口是否被占用（说明应用已启动）
        if ! check_port $port; then
            # 查找监听该端口的 Java 进程
            java_pid=$(lsof -ti :$port 2>/dev/null | head -1)
            if [ -n "$java_pid" ]; then
                echo $java_pid > "$pid_file"
                echo_info "✅ Instance on port $port started successfully (Java PID: $java_pid)"
                echo_info "   Log file: $log_file"
                echo_info "   PID file: $pid_file"
                return 0
            fi
        fi
        sleep 1
        waited=$((waited + 1))
    done
    
    # 启动失败
    echo_error "Failed to start instance on port $port (timeout after ${max_wait}s)"
    echo_error "Check log file: $log_file"
    rm -f "$pid_file"
    return 1
}

# 停止单个实例
stop_instance() {
    local port=$1
    local pid_file="$PID_DIR/router-${port}.pid"
    
    if [ ! -f "$pid_file" ]; then
        # 尝试通过端口查找进程
        local java_pid=$(lsof -ti :$port 2>/dev/null | head -1)
        if [ -n "$java_pid" ]; then
            echo_warn "Instance on port $port is running (PID: $java_pid) but not managed by this script"
            read -p "Stop it anyway? (y/N) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                kill "$java_pid" 2>/dev/null || true
                sleep 2
                if ps -p "$java_pid" > /dev/null 2>&1; then
                    kill -9 "$java_pid" 2>/dev/null || true
                fi
                echo_info "✅ Instance on port $port stopped"
            fi
        else
            echo_warn "Instance on port $port is not running (no PID file and port not in use)"
        fi
        return 0
    fi
    
    local pid=$(cat "$pid_file")
    if ! ps -p "$pid" > /dev/null 2>&1; then
        echo_warn "Instance on port $port is not running (PID: $pid not found)"
        rm -f "$pid_file"
        # 检查端口是否还被占用
        local java_pid=$(lsof -ti :$port 2>/dev/null | head -1)
        if [ -n "$java_pid" ]; then
            echo_warn "Port $port is still in use by PID: $java_pid, stopping it..."
            kill "$java_pid" 2>/dev/null || true
            sleep 2
            if ps -p "$java_pid" > /dev/null 2>&1; then
                kill -9 "$java_pid" 2>/dev/null || true
            fi
        fi
        return 0
    fi
    
    echo_info "Stopping instance on port $port (PID: $pid)..."
    
    # 查找所有相关进程（Java 进程和可能的 Maven 父进程）
    local java_pid=$(lsof -ti :$port 2>/dev/null | head -1)
    local maven_pids=$(ps -o ppid= -p "$pid" 2>/dev/null | xargs)
    
    # 先停止 Java 进程
    if [ -n "$java_pid" ] && [ "$java_pid" != "$pid" ]; then
        kill "$java_pid" 2>/dev/null || true
    else
        kill "$pid" 2>/dev/null || true
    fi
    
    # 等待进程退出
    local count=0
    while (ps -p "$pid" > /dev/null 2>&1 || ([ -n "$java_pid" ] && ps -p "$java_pid" > /dev/null 2>&1)) && [ $count -lt 10 ]; do
        sleep 1
        count=$((count + 1))
    done
    
    # 如果还没退出，强制 kill
    if ps -p "$pid" > /dev/null 2>&1; then
        echo_warn "Process did not stop gracefully, forcing kill..."
        kill -9 "$pid" 2>/dev/null || true
    fi
    if [ -n "$java_pid" ] && ps -p "$java_pid" > /dev/null 2>&1; then
        kill -9 "$java_pid" 2>/dev/null || true
    fi
    
    # 停止 Maven 父进程（如果有）
    for maven_pid in $maven_pids; do
        if [ -n "$maven_pid" ] && ps -p "$maven_pid" > /dev/null 2>&1; then
            echo_info "Stopping Maven parent process (PID: $maven_pid)..."
            kill "$maven_pid" 2>/dev/null || true
            sleep 1
            if ps -p "$maven_pid" > /dev/null 2>&1; then
                kill -9 "$maven_pid" 2>/dev/null || true
            fi
        fi
    done
    
    rm -f "$pid_file"
    
    # 最后检查端口是否已释放
    sleep 1
    if ! check_port $port; then
        local remaining_pid=$(lsof -ti :$port 2>/dev/null | head -1)
        if [ -n "$remaining_pid" ]; then
            echo_warn "Port $port is still in use by PID: $remaining_pid, stopping it..."
            kill -9 "$remaining_pid" 2>/dev/null || true
        fi
    fi
    
    echo_info "✅ Instance on port $port stopped"
}

# 主函数
main() {
    case "${1:-start}" in
        start)
            echo_info "Starting MCP Router V3 instances..."
            start_instance 8051 "router-instance-1"
            start_instance 8052 "router-instance-2"
            start_instance 8053 "router-instance-3"
            echo_info "All instances started. Check logs in $LOG_DIR"
            ;;
        stop)
            echo_info "Stopping MCP Router V3 instances..."
            stop_instance 8051
            stop_instance 8052
            stop_instance 8053
            echo_info "All instances stopped"
            ;;
        restart)
            echo_info "Restarting MCP Router V3 instances..."
            stop_instance 8051
            stop_instance 8052
            stop_instance 8053
            sleep 2
            start_instance 8051 "router-instance-1"
            start_instance 8052 "router-instance-2"
            start_instance 8053 "router-instance-3"
            ;;
        status)
            echo_info "MCP Router V3 instances status:"
            for port in 8051 8052 8053; do
                local pid_file="$PID_DIR/router-${port}.pid"
                if [ -f "$pid_file" ]; then
                    local pid=$(cat "$pid_file")
                    if ps -p "$pid" > /dev/null 2>&1; then
                        # 检查端口是否真的在监听
                        if ! check_port $port; then
                            # 尝试获取健康状态
                            local health=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
                            if [ "$health" = "200" ]; then
                                echo_info "  Port $port: ✅ Running (PID: $pid, Health: OK)"
                            else
                                echo_warn "  Port $port: ⚠️  Running (PID: $pid, Health: $health)"
                            fi
                        else
                            echo_warn "  Port $port: ⚠️  Process running but port not listening (PID: $pid)"
                        fi
                    else
                        echo_warn "  Port $port: ❌ Not running (stale PID file: $pid)"
                        rm -f "$pid_file"
                    fi
                else
                    # 检查端口是否被占用（可能是手动启动的）
                    if ! check_port $port; then
                        local java_pid=$(lsof -ti :$port 2>/dev/null | head -1)
                        if [ -n "$java_pid" ]; then
                            echo_warn "  Port $port: ⚠️  Port in use (PID: $java_pid) but not managed by this script"
                        else
                            echo_warn "  Port $port: ❌ Not running"
                        fi
                    else
                        echo_warn "  Port $port: ❌ Not running"
                    fi
                fi
            done
            ;;
        logs)
            local port="${2:-}"
            if [ -z "$port" ]; then
                echo_info "Showing logs for all instances (last 50 lines each):"
                for port in 8051 8052 8053; do
                    local log_file="$LOG_DIR/router-${port}.log"
                    if [ -f "$log_file" ]; then
                        echo_info ""
                        echo_info "=== Port $port ==="
                        tail -50 "$log_file"
                    fi
                done
            else
                local log_file="$LOG_DIR/router-${port}.log"
                if [ -f "$log_file" ]; then
                    tail -f "$log_file"
                else
                    echo_error "Log file not found: $log_file"
                    exit 1
                fi
            fi
            ;;
        *)
            echo "Usage: $0 {start|stop|restart|status|logs [port]}"
            echo ""
            echo "Commands:"
            echo "  start          Start all instances (8051, 8052, 8053)"
            echo "  stop           Stop all instances"
            echo "  restart        Restart all instances"
            echo "  status         Show status of all instances"
            echo "  logs [port]    Show logs (all instances or specific port)"
            echo ""
            echo "Examples:"
            echo "  $0 start                    # Start all instances"
            echo "  $0 status                   # Check status"
            echo "  $0 logs                     # Show logs for all instances"
            echo "  $0 logs 8051                # Follow logs for port 8051"
            exit 1
            ;;
    esac
}

main "$@"

