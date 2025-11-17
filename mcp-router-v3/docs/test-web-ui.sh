#!/bin/bash

# MCP Router v3 Web UI 测试脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}🌐 MCP Router v3 Web UI 测试${NC}"
echo ""

# 应用配置
APP_HOST="localhost"
APP_PORT="8052"
BASE_URL="http://${APP_HOST}:${APP_PORT}"

# 检查应用是否运行
check_application() {
    echo -e "${BLUE}1. 检查应用状态...${NC}"
    
    if curl -s "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
        echo -e "${GREEN}✅ 应用正在运行${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  应用未运行，尝试启动...${NC}"
        return 1
    fi
}

# 启动应用
start_application() {
    echo -e "${BLUE}2. 启动应用...${NC}"
    
    if [ -f "./debug-start.sh" ]; then
        echo -e "${YELLOW}使用调试模式启动应用...${NC}"
        ./debug-start.sh full >/dev/null 2>&1 &
        
        # 等待应用启动
        echo -e "${YELLOW}等待应用启动...${NC}"
        for i in {1..30}; do
            if curl -s "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
                echo -e "${GREEN}✅ 应用启动成功${NC}"
                return 0
            fi
            sleep 2
            echo -n "."
        done
        echo ""
        echo -e "${RED}❌ 应用启动超时${NC}"
        return 1
    else
        echo -e "${RED}❌ 找不到启动脚本${NC}"
        return 1
    fi
}

# 测试页面访问
test_pages() {
    echo -e "${BLUE}3. 测试页面访问...${NC}"
    
    # 定义要测试的页面
    declare -A pages=(
        ["仪表板"]="/"
        ["服务器管理"]="/servers"
        ["健康监控"]="/health"
        ["路由日志"]="/logs"
        ["系统配置"]="/config"
    )
    
    local success_count=0
    local total_count=${#pages[@]}
    
    for page_name in "${!pages[@]}"; do
        local page_url="${pages[$page_name]}"
        local full_url="${BASE_URL}${page_url}"
        
        echo -n "  测试 ${page_name} (${page_url})... "
        
        if curl -s -o /dev/null -w "%{http_code}" "${full_url}" | grep -q "200"; then
            echo -e "${GREEN}✅${NC}"
            ((success_count++))
        else
            echo -e "${RED}❌${NC}"
        fi
    done
    
    echo ""
    echo -e "页面测试结果: ${success_count}/${total_count} 成功"
    
    if [ $success_count -eq $total_count ]; then
        echo -e "${GREEN}✅ 所有页面访问正常${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  部分页面访问异常${NC}"
        return 1
    fi
}

# 测试API接口
test_apis() {
    echo -e "${BLUE}4. 测试API接口...${NC}"
    
    # 定义要测试的API
    declare -A apis=(
        ["服务器列表"]="/api/mcp/servers"
        ["健康检查摘要"]="/api/health/summary"
        ["系统统计"]="/api/stats/overview"
        ["最近日志"]="/api/logs/recent?limit=10"
        ["系统配置"]="/api/config/all"
    )
    
    local success_count=0
    local total_count=${#apis[@]}
    
    for api_name in "${!apis[@]}"; do
        local api_url="${apis[$api_name]}"
        local full_url="${BASE_URL}${api_url}"
        
        echo -n "  测试 ${api_name} (${api_url})... "
        
        local response_code=$(curl -s -o /dev/null -w "%{http_code}" "${full_url}")
        
        if [ "$response_code" = "200" ]; then
            echo -e "${GREEN}✅${NC}"
            ((success_count++))
        else
            echo -e "${RED}❌ (HTTP ${response_code})${NC}"
        fi
    done
    
    echo ""
    echo -e "API测试结果: ${success_count}/${total_count} 成功"
    
    if [ $success_count -eq $total_count ]; then
        echo -e "${GREEN}✅ 所有API接口正常${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  部分API接口异常${NC}"
        return 1
    fi
}

# 测试静态资源
test_static_resources() {
    echo -e "${BLUE}5. 测试静态资源...${NC}"
    
    # 定义要测试的静态资源
    declare -A resources=(
        ["样式文件"]="/css/style.css"
        ["JavaScript文件"]="/js/app.js"
    )
    
    local success_count=0
    local total_count=${#resources[@]}
    
    for resource_name in "${!resources[@]}"; do
        local resource_url="${resources[$resource_name]}"
        local full_url="${BASE_URL}${resource_url}"
        
        echo -n "  测试 ${resource_name} (${resource_url})... "
        
        local response_code=$(curl -s -o /dev/null -w "%{http_code}" "${full_url}")
        
        if [ "$response_code" = "200" ]; then
            echo -e "${GREEN}✅${NC}"
            ((success_count++))
        else
            echo -e "${RED}❌ (HTTP ${response_code})${NC}"
        fi
    done
    
    echo ""
    echo -e "静态资源测试结果: ${success_count}/${total_count} 成功"
    
    if [ $success_count -eq $total_count ]; then
        echo -e "${GREEN}✅ 所有静态资源正常${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  部分静态资源异常${NC}"
        return 1
    fi
}

# 性能测试
test_performance() {
    echo -e "${BLUE}6. 性能测试...${NC}"
    
    echo -n "  测试首页响应时间... "
    local response_time=$(curl -s -o /dev/null -w "%{time_total}" "${BASE_URL}/")
    local response_time_ms=$(echo "$response_time * 1000" | bc -l | cut -d. -f1)
    
    if [ "$response_time_ms" -lt 1000 ]; then
        echo -e "${GREEN}✅ ${response_time_ms}ms${NC}"
    elif [ "$response_time_ms" -lt 3000 ]; then
        echo -e "${YELLOW}⚠️  ${response_time_ms}ms (较慢)${NC}"
    else
        echo -e "${RED}❌ ${response_time_ms}ms (太慢)${NC}"
    fi
    
    echo -n "  测试API响应时间... "
    local api_response_time=$(curl -s -o /dev/null -w "%{time_total}" "${BASE_URL}/api/mcp/servers")
    local api_response_time_ms=$(echo "$api_response_time * 1000" | bc -l | cut -d. -f1)
    
    if [ "$api_response_time_ms" -lt 500 ]; then
        echo -e "${GREEN}✅ ${api_response_time_ms}ms${NC}"
    elif [ "$api_response_time_ms" -lt 1000 ]; then
        echo -e "${YELLOW}⚠️  ${api_response_time_ms}ms (较慢)${NC}"
    else
        echo -e "${RED}❌ ${api_response_time_ms}ms (太慢)${NC}"
    fi
}

# 显示访问信息
show_access_info() {
    echo ""
    echo -e "${CYAN}🌐 Web UI 访问信息${NC}"
    echo -e "${BLUE}主页地址: ${YELLOW}${BASE_URL}${NC}"
    echo -e "${BLUE}仪表板: ${YELLOW}${BASE_URL}/${NC}"
    echo -e "${BLUE}服务器管理: ${YELLOW}${BASE_URL}/servers${NC}"
    echo -e "${BLUE}健康监控: ${YELLOW}${BASE_URL}/health${NC}"
    echo -e "${BLUE}路由日志: ${YELLOW}${BASE_URL}/logs${NC}"
    echo -e "${BLUE}系统配置: ${YELLOW}${BASE_URL}/config${NC}"
    echo ""
    echo -e "${BLUE}API文档: ${YELLOW}${BASE_URL}/actuator${NC}"
    echo -e "${BLUE}健康检查: ${YELLOW}${BASE_URL}/actuator/health${NC}"
    echo ""
}

# 显示使用建议
show_usage_tips() {
    echo -e "${CYAN}💡 使用建议${NC}"
    echo -e "1. 首次访问建议从仪表板开始，了解系统整体状态"
    echo -e "2. 在服务器管理页面可以添加和管理MCP服务器"
    echo -e "3. 健康监控页面提供详细的健康检查信息"
    echo -e "4. 路由日志页面可以分析请求性能和错误"
    echo -e "5. 系统配置页面可以调整各种系统参数"
    echo ""
    echo -e "${BLUE}🔧 调试命令:${NC}"
    echo -e "  查看应用日志: ${YELLOW}./debug-log-analyzer.sh tail${NC}"
    echo -e "  停止应用: ${YELLOW}pkill -f mcp-router-v3${NC}"
    echo -e "  重启应用: ${YELLOW}./debug-start.sh${NC}"
    echo ""
}

# 主函数
main() {
    local app_was_running=false
    
    # 检查应用状态
    if check_application; then
        app_was_running=true
    else
        # 尝试启动应用
        if ! start_application; then
            echo -e "${RED}❌ 无法启动应用，测试终止${NC}"
            exit 1
        fi
    fi
    
    # 运行测试
    local test_results=()
    
    if test_pages; then
        test_results+=("页面测试: ✅")
    else
        test_results+=("页面测试: ❌")
    fi
    
    if test_apis; then
        test_results+=("API测试: ✅")
    else
        test_results+=("API测试: ❌")
    fi
    
    if test_static_resources; then
        test_results+=("静态资源测试: ✅")
    else
        test_results+=("静态资源测试: ❌")
    fi
    
    test_performance
    
    # 显示测试结果
    echo ""
    echo -e "${CYAN}📋 测试结果总结${NC}"
    for result in "${test_results[@]}"; do
        echo -e "  $result"
    done
    
    # 显示访问信息
    show_access_info
    
    # 显示使用建议
    show_usage_tips
    
    echo -e "${GREEN}🎉 Web UI 测试完成！${NC}"
    
    if [ "$app_was_running" = false ]; then
        echo -e "${YELLOW}💡 应用已在后台启动，可以开始使用Web UI${NC}"
    fi
}

# 执行主函数
main "$@"

