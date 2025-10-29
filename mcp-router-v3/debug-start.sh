#!/bin/bash

# MCP Router v3 调试启动脚本
# 提供多种调试模式和日志级别选择

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

# 默认配置
DEFAULT_PROFILE="debug"
DEFAULT_PORT="8052"
DEFAULT_LOG_LEVEL="DEBUG"

# 打印帮助信息
print_help() {
    echo -e "${CYAN}MCP Router v3 调试启动脚本${NC}"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -p, --profile PROFILE    Spring 配置文件 (默认: debug)"
    echo "  -P, --port PORT          服务端口 (默认: 8052)"
    echo "  -l, --log-level LEVEL    日志级别 (默认: DEBUG)"
    echo "  -m, --mode MODE          调试模式:"
    echo "                             full    - 完整调试 (默认)"
    echo "                             perf    - 性能监控"
    echo "                             trace   - 请求跟踪"
    echo "                             mcp     - MCP协议调试"
    echo "                             health  - 健康检查调试"
    echo "  -c, --clean              清理日志文件"
    echo "  -b, --background         后台运行"
    echo "  -h, --help               显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                       # 使用默认配置启动"
    echo "  $0 -m perf              # 启动性能监控模式"
    echo "  $0 -l TRACE -m trace    # 启动请求跟踪模式"
    echo "  $0 -c                   # 清理日志文件"
    echo ""
}

# 清理日志文件
clean_logs() {
    echo -e "${YELLOW}🧹 清理日志文件...${NC}"
    
    if [ -d "$PROJECT_DIR/logs" ]; then
        find "$PROJECT_DIR/logs" -name "*.log" -type f -delete
        find "$PROJECT_DIR/logs" -name "*.gz" -type f -delete
        echo -e "${GREEN}✅ 日志文件已清理${NC}"
    else
        echo -e "${BLUE}ℹ️  日志目录不存在，无需清理${NC}"
    fi
}

# 检查端口是否被占用
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${RED}❌ 端口 $port 已被占用${NC}"
        echo -e "${YELLOW}正在使用端口 $port 的进程:${NC}"
        lsof -Pi :$port -sTCP:LISTEN
        echo ""
        read -p "是否要终止占用端口的进程? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo -e "${YELLOW}🔄 终止占用端口 $port 的进程...${NC}"
            lsof -Pi :$port -sTCP:LISTEN -t | xargs kill -9
            sleep 2
            echo -e "${GREEN}✅ 进程已终止${NC}"
        else
            echo -e "${RED}❌ 启动取消${NC}"
            exit 1
        fi
    fi
}

# 创建日志目录
create_log_dir() {
    if [ ! -d "$PROJECT_DIR/logs" ]; then
        mkdir -p "$PROJECT_DIR/logs"
        echo -e "${GREEN}✅ 创建日志目录: $PROJECT_DIR/logs${NC}"
    fi
}

# 设置调试模式的JVM参数
get_debug_jvm_args() {
    local mode=$1
    local jvm_args=""
    
    case $mode in
        "full")
            jvm_args="-Xms512m -Xmx1024m -XX:+UseG1GC"
            jvm_args="$jvm_args -Ddebug.enabled=true"
            jvm_args="$jvm_args -Ddebug.request-tracking.enabled=true"
            jvm_args="$jvm_args -Ddebug.performance.enabled=true"
            jvm_args="$jvm_args -Ddebug.mcp-protocol.log-messages=true"
            ;;
        "perf")
            jvm_args="-Xms512m -Xmx1024m -XX:+UseG1GC"
            jvm_args="$jvm_args -Ddebug.performance.enabled=true"
            jvm_args="$jvm_args -Ddebug.performance.log-all-requests=true"
            jvm_args="$jvm_args -Ddebug.connection-pool.log-detailed-stats=true"
            ;;
        "trace")
            jvm_args="-Xms512m -Xmx1024m"
            jvm_args="$jvm_args -Ddebug.request-tracking.enabled=true"
            jvm_args="$jvm_args -Ddebug.request-tracking.max-contexts=2000"
            ;;
        "mcp")
            jvm_args="-Xms512m -Xmx1024m"
            jvm_args="$jvm_args -Ddebug.mcp-protocol.log-messages=true"
            jvm_args="$jvm_args -Ddebug.mcp-protocol.log-handshake=true"
            jvm_args="$jvm_args -Ddebug.mcp-protocol.log-errors=true"
            ;;
        "health")
            jvm_args="-Xms512m -Xmx1024m"
            jvm_args="$jvm_args -Ddebug.health-check.log-all-checks=true"
            jvm_args="$jvm_args -Ddebug.health-check.log-timing=true"
            ;;
        *)
            jvm_args="-Xms512m -Xmx1024m"
            ;;
    esac
    
    echo "$jvm_args"
}

# 启动应用
start_application() {
    local profile=$1
    local port=$2
    local log_level=$3
    local mode=$4
    local background=$5
    
    echo -e "${CYAN}🚀 启动 MCP Router v3 调试模式${NC}"
    echo -e "${BLUE}配置信息:${NC}"
    echo -e "  Profile: ${YELLOW}$profile${NC}"
    echo -e "  Port: ${YELLOW}$port${NC}"
    echo -e "  Log Level: ${YELLOW}$log_level${NC}"
    echo -e "  Debug Mode: ${YELLOW}$mode${NC}"
    echo -e "  Background: ${YELLOW}$background${NC}"
    echo ""
    
    # 获取JVM参数
    local jvm_args=$(get_debug_jvm_args "$mode")
    
    # 构建Maven命令
    local mvn_cmd="mvn spring-boot:run"
    mvn_cmd="$mvn_cmd -Dspring-boot.run.profiles=$profile"
    mvn_cmd="$mvn_cmd -Dspring-boot.run.jvmArguments=\"$jvm_args -Dserver.port=$port -Dlogging.level.root=$log_level\""
    
    echo -e "${PURPLE}执行命令:${NC}"
    echo -e "${CYAN}$mvn_cmd${NC}"
    echo ""
    
    # 切换到项目目录
    cd "$PROJECT_DIR"
    
    if [ "$background" = "true" ]; then
        echo -e "${YELLOW}🔄 后台启动应用...${NC}"
        nohup bash -c "$mvn_cmd" > logs/startup.log 2>&1 &
        local pid=$!
        echo -e "${GREEN}✅ 应用已在后台启动 (PID: $pid)${NC}"
        echo -e "${BLUE}查看启动日志: tail -f logs/startup.log${NC}"
        echo -e "${BLUE}查看应用日志: tail -f logs/mcp-router-v3-debug.log${NC}"
        echo -e "${BLUE}停止应用: kill $pid${NC}"
    else
        echo -e "${YELLOW}🔄 启动应用...${NC}"
        echo -e "${BLUE}按 Ctrl+C 停止应用${NC}"
        echo ""
        eval "$mvn_cmd"
    fi
}

# 主函数
main() {
    local profile="$DEFAULT_PROFILE"
    local port="$DEFAULT_PORT"
    local log_level="$DEFAULT_LOG_LEVEL"
    local mode="full"
    local background="false"
    local clean="false"
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -p|--profile)
                profile="$2"
                shift 2
                ;;
            -P|--port)
                port="$2"
                shift 2
                ;;
            -l|--log-level)
                log_level="$2"
                shift 2
                ;;
            -m|--mode)
                mode="$2"
                shift 2
                ;;
            -c|--clean)
                clean="true"
                shift
                ;;
            -b|--background)
                background="true"
                shift
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            *)
                echo -e "${RED}❌ 未知选项: $1${NC}"
                print_help
                exit 1
                ;;
        esac
    done
    
    # 清理日志文件
    if [ "$clean" = "true" ]; then
        clean_logs
        if [ $# -eq 1 ]; then  # 如果只有 -c 参数，清理后退出
            exit 0
        fi
    fi
    
    # 创建日志目录
    create_log_dir
    
    # 检查端口
    check_port "$port"
    
    # 启动应用
    start_application "$profile" "$port" "$log_level" "$mode" "$background"
}

# 执行主函数
main "$@"
