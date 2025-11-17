#!/bin/bash

# MCP Router v3 日志分析工具
# 提供各种日志分析和监控功能

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
LOG_DIR="$SCRIPT_DIR/logs"

# 打印帮助信息
print_help() {
    echo -e "${CYAN}MCP Router v3 日志分析工具${NC}"
    echo ""
    echo "用法: $0 [命令] [选项]"
    echo ""
    echo "命令:"
    echo "  tail                     实时查看日志"
    echo "  errors                   查看错误日志"
    echo "  performance              查看性能日志"
    echo "  connections              查看连接日志"
    echo "  health                   查看健康检查日志"
    echo "  routing                  查看路由日志"
    echo "  stats                    显示日志统计"
    echo "  search PATTERN           搜索日志内容"
    echo "  analyze                  分析日志模式"
    echo ""
    echo "选项:"
    echo "  -f, --file FILE          指定日志文件"
    echo "  -n, --lines NUM          显示行数 (默认: 100)"
    echo "  -t, --time MINUTES       显示最近N分钟的日志"
    echo "  -s, --server SERVER      过滤特定服务器"
    echo "  -h, --help               显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 tail                  # 实时查看所有日志"
    echo "  $0 errors -n 50          # 查看最近50行错误日志"
    echo "  $0 performance -t 10     # 查看最近10分钟的性能日志"
    echo "  $0 search \"connection\"   # 搜索包含'connection'的日志"
    echo ""
}

# 检查日志目录
check_log_dir() {
    if [ ! -d "$LOG_DIR" ]; then
        echo -e "${RED}❌ 日志目录不存在: $LOG_DIR${NC}"
        echo -e "${YELLOW}请先启动应用生成日志文件${NC}"
        exit 1
    fi
}

# 获取日志文件列表
get_log_files() {
    local pattern=$1
    find "$LOG_DIR" -name "*$pattern*.log" -type f 2>/dev/null | sort -r
}

# 实时查看日志
tail_logs() {
    local lines=${1:-100}
    local file_pattern=${2:-""}
    
    echo -e "${CYAN}📊 实时查看日志 (最近 $lines 行)${NC}"
    echo -e "${BLUE}按 Ctrl+C 停止${NC}"
    echo ""
    
    if [ -n "$file_pattern" ]; then
        local files=$(get_log_files "$file_pattern")
        if [ -z "$files" ]; then
            echo -e "${RED}❌ 未找到匹配的日志文件: *$file_pattern*.log${NC}"
            exit 1
        fi
        tail -n "$lines" -f $files
    else
        local all_files=$(get_log_files "")
        if [ -z "$all_files" ]; then
            echo -e "${RED}❌ 未找到任何日志文件${NC}"
            exit 1
        fi
        tail -n "$lines" -f $all_files
    fi
}

# 查看错误日志
show_errors() {
    local lines=${1:-100}
    local time_filter=${2:-""}
    
    echo -e "${RED}🚨 错误日志分析${NC}"
    echo ""
    
    local error_files=$(get_log_files "error")
    if [ -z "$error_files" ]; then
        echo -e "${YELLOW}⚠️  未找到错误日志文件${NC}"
        return
    fi
    
    if [ -n "$time_filter" ]; then
        echo -e "${BLUE}最近 $time_filter 分钟的错误:${NC}"
        find "$LOG_DIR" -name "*error*.log" -type f -newermt "$time_filter minutes ago" -exec cat {} \; | tail -n "$lines"
    else
        echo -e "${BLUE}最近 $lines 行错误:${NC}"
        tail -n "$lines" $error_files
    fi
    
    echo ""
    echo -e "${PURPLE}错误统计:${NC}"
    cat $error_files | grep -E "(ERROR|FATAL)" | awk '{print $4}' | sort | uniq -c | sort -nr | head -10
}

# 查看性能日志
show_performance() {
    local lines=${1:-100}
    local time_filter=${2:-""}
    
    echo -e "${GREEN}📈 性能日志分析${NC}"
    echo ""
    
    local perf_files=$(get_log_files "performance")
    if [ -z "$perf_files" ]; then
        echo -e "${YELLOW}⚠️  未找到性能日志文件${NC}"
        return
    fi
    
    if [ -n "$time_filter" ]; then
        echo -e "${BLUE}最近 $time_filter 分钟的性能数据:${NC}"
        find "$LOG_DIR" -name "*performance*.log" -type f -newermt "$time_filter minutes ago" -exec cat {} \; | tail -n "$lines"
    else
        echo -e "${BLUE}最近 $lines 行性能数据:${NC}"
        tail -n "$lines" $perf_files
    fi
    
    echo ""
    echo -e "${PURPLE}性能统计:${NC}"
    
    # 连接创建统计
    echo -e "${CYAN}连接创建:${NC}"
    cat $perf_files | grep "CONNECTION_CREATED" | wc -l | xargs echo "  总数:"
    
    # 健康检查统计
    echo -e "${CYAN}健康检查:${NC}"
    cat $perf_files | grep "HEALTH_CHECK_SUCCESS" | wc -l | xargs echo "  成功:"
    cat $perf_files | grep "HEALTH_CHECK.*FAILED" | wc -l | xargs echo "  失败:"
    
    # 路由统计
    echo -e "${CYAN}路由请求:${NC}"
    cat $perf_files | grep "ROUTE_SUCCESS" | wc -l | xargs echo "  成功:"
    cat $perf_files | grep "ROUTE_FAILED" | wc -l | xargs echo "  失败:"
}

# 查看连接日志
show_connections() {
    local lines=${1:-100}
    local server_filter=${2:-""}
    
    echo -e "${BLUE}🔗 连接日志分析${NC}"
    echo ""
    
    local log_files=$(get_log_files "mcp")
    if [ -z "$log_files" ]; then
        log_files=$(get_log_files "")
    fi
    
    if [ -z "$log_files" ]; then
        echo -e "${RED}❌ 未找到日志文件${NC}"
        return
    fi
    
    local grep_pattern="CONNECTION"
    if [ -n "$server_filter" ]; then
        grep_pattern="CONNECTION.*$server_filter"
    fi
    
    echo -e "${BLUE}连接事件 (最近 $lines 行):${NC}"
    cat $log_files | grep -E "$grep_pattern" | tail -n "$lines"
    
    echo ""
    echo -e "${PURPLE}连接统计:${NC}"
    cat $log_files | grep "CONNECTION_CREATED" | wc -l | xargs echo "  创建:"
    cat $log_files | grep "CONNECTION.*FAILED" | wc -l | xargs echo "  失败:"
    cat $log_files | grep "CACHE_HIT" | wc -l | xargs echo "  缓存命中:"
}

# 查看健康检查日志
show_health() {
    local lines=${1:-100}
    local server_filter=${2:-""}
    
    echo -e "${GREEN}💚 健康检查日志分析${NC}"
    echo ""
    
    local log_files=$(get_log_files "")
    if [ -z "$log_files" ]; then
        echo -e "${RED}❌ 未找到日志文件${NC}"
        return
    fi
    
    local grep_pattern="HEALTH_CHECK"
    if [ -n "$server_filter" ]; then
        grep_pattern="HEALTH_CHECK.*$server_filter"
    fi
    
    echo -e "${BLUE}健康检查事件 (最近 $lines 行):${NC}"
    cat $log_files | grep -E "$grep_pattern" | tail -n "$lines"
    
    echo ""
    echo -e "${PURPLE}健康检查统计:${NC}"
    cat $log_files | grep "checkServerHealthLayered.*HEALTHY" | wc -l | xargs echo "  健康:"
    cat $log_files | grep "checkServerHealthLayered.*UNHEALTHY" | wc -l | xargs echo "  不健康:"
    cat $log_files | grep "Level 1.*failed" | wc -l | xargs echo "  Nacos检查失败:"
    cat $log_files | grep "Level 2.*failed" | wc -l | xargs echo "  MCP检查失败:"
}

# 查看路由日志
show_routing() {
    local lines=${1:-100}
    local server_filter=${2:-""}
    
    echo -e "${PURPLE}🚀 路由日志分析${NC}"
    echo ""
    
    local log_files=$(get_log_files "")
    if [ -z "$log_files" ]; then
        echo -e "${RED}❌ 未找到日志文件${NC}"
        return
    fi
    
    local grep_pattern="ROUTING"
    if [ -n "$server_filter" ]; then
        grep_pattern="ROUTING.*$server_filter"
    fi
    
    echo -e "${BLUE}路由事件 (最近 $lines 行):${NC}"
    cat $log_files | grep -E "$grep_pattern" | tail -n "$lines"
    
    echo ""
    echo -e "${PURPLE}路由统计:${NC}"
    cat $log_files | grep "routeMessage.*Successfully" | wc -l | xargs echo "  成功:"
    cat $log_files | grep "routeMessage.*Failed" | wc -l | xargs echo "  失败:"
}

# 显示日志统计
show_stats() {
    echo -e "${CYAN}📊 日志文件统计${NC}"
    echo ""
    
    if [ ! -d "$LOG_DIR" ]; then
        echo -e "${RED}❌ 日志目录不存在${NC}"
        return
    fi
    
    echo -e "${BLUE}日志文件:${NC}"
    ls -lh "$LOG_DIR"/*.log 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
    
    echo ""
    echo -e "${BLUE}日志行数统计:${NC}"
    for file in "$LOG_DIR"/*.log; do
        if [ -f "$file" ]; then
            local count=$(wc -l < "$file")
            local basename=$(basename "$file")
            echo "  $basename: $count 行"
        fi
    done
    
    echo ""
    echo -e "${BLUE}最近1小时活动:${NC}"
    find "$LOG_DIR" -name "*.log" -type f -newermt "1 hour ago" -exec wc -l {} \; | awk '{sum+=$1} END {print "  新增日志: " sum " 行"}'
}

# 搜索日志内容
search_logs() {
    local pattern=$1
    local lines=${2:-100}
    
    if [ -z "$pattern" ]; then
        echo -e "${RED}❌ 请提供搜索模式${NC}"
        exit 1
    fi
    
    echo -e "${CYAN}🔍 搜索日志内容: '$pattern'${NC}"
    echo ""
    
    local log_files=$(get_log_files "")
    if [ -z "$log_files" ]; then
        echo -e "${RED}❌ 未找到日志文件${NC}"
        return
    fi
    
    echo -e "${BLUE}搜索结果 (最近 $lines 行):${NC}"
    grep -h -i "$pattern" $log_files | tail -n "$lines"
    
    echo ""
    echo -e "${PURPLE}匹配统计:${NC}"
    grep -h -i "$pattern" $log_files | wc -l | xargs echo "  总匹配数:"
}

# 分析日志模式
analyze_logs() {
    echo -e "${CYAN}🔬 日志模式分析${NC}"
    echo ""
    
    local log_files=$(get_log_files "")
    if [ -z "$log_files" ]; then
        echo -e "${RED}❌ 未找到日志文件${NC}"
        return
    fi
    
    echo -e "${BLUE}日志级别分布:${NC}"
    cat $log_files | grep -E "(DEBUG|INFO|WARN|ERROR|FATAL)" | awk '{print $3}' | sort | uniq -c | sort -nr
    
    echo ""
    echo -e "${BLUE}最频繁的日志消息 (Top 10):${NC}"
    cat $log_files | awk -F'] ' '{print $2}' | awk '{print $1 " " $2 " " $3}' | sort | uniq -c | sort -nr | head -10
    
    echo ""
    echo -e "${BLUE}错误模式分析:${NC}"
    cat $log_files | grep -E "ERROR|Exception|Failed" | awk -F': ' '{print $2}' | sort | uniq -c | sort -nr | head -5
    
    echo ""
    echo -e "${BLUE}时间分布 (按小时):${NC}"
    cat $log_files | awk '{print $1 " " $2}' | awk -F'T' '{print $1 " " substr($2,1,2)}' | sort | uniq -c | tail -24
}

# 主函数
main() {
    local command=""
    local lines=100
    local time_filter=""
    local server_filter=""
    local file_pattern=""
    local search_pattern=""
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            tail|errors|performance|connections|health|routing|stats|analyze)
                command="$1"
                shift
                ;;
            search)
                command="search"
                search_pattern="$2"
                shift 2
                ;;
            -f|--file)
                file_pattern="$2"
                shift 2
                ;;
            -n|--lines)
                lines="$2"
                shift 2
                ;;
            -t|--time)
                time_filter="$2"
                shift 2
                ;;
            -s|--server)
                server_filter="$2"
                shift 2
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
    
    # 如果没有指定命令，显示帮助
    if [ -z "$command" ]; then
        print_help
        exit 0
    fi
    
    # 检查日志目录
    check_log_dir
    
    # 执行相应命令
    case $command in
        "tail")
            tail_logs "$lines" "$file_pattern"
            ;;
        "errors")
            show_errors "$lines" "$time_filter"
            ;;
        "performance")
            show_performance "$lines" "$time_filter"
            ;;
        "connections")
            show_connections "$lines" "$server_filter"
            ;;
        "health")
            show_health "$lines" "$server_filter"
            ;;
        "routing")
            show_routing "$lines" "$server_filter"
            ;;
        "stats")
            show_stats
            ;;
        "search")
            search_logs "$search_pattern" "$lines"
            ;;
        "analyze")
            analyze_logs
            ;;
        *)
            echo -e "${RED}❌ 未知命令: $command${NC}"
            print_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"

