#!/bin/bash

# 部署前检查清单脚本
# 检查所有必要的配置和依赖

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo_info() {
    echo -e "${GREEN}[✓]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[⚠]${NC} $1"
}

echo_error() {
    echo -e "${RED}[✗]${NC} $1"
}

echo_step() {
    echo -e "${BLUE}[→]${NC} $1"
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

failed_checks=0
warnings=0

echo ""
echo "=========================================="
echo "  MCP Router V3 部署前检查清单"
echo "=========================================="
echo ""

# 1. 检查必要的工具
echo_step "1. 检查必要的工具..."

if command -v mvn >/dev/null 2>&1; then
    echo_info "Maven 已安装: $(mvn -version | head -1)"
else
    echo_error "Maven 未安装"
    failed_checks=$((failed_checks + 1))
fi

if command -v java >/dev/null 2>&1; then
    java_version=$(java -version 2>&1 | head -1)
    echo_info "Java 已安装: $java_version"
else
    echo_error "Java 未安装"
    failed_checks=$((failed_checks + 1))
fi

if command -v nginx >/dev/null 2>&1; then
    echo_info "Nginx 已安装: $(nginx -v 2>&1)"
else
    echo_warn "Nginx 未安装（可选，用于负载均衡）"
    warnings=$((warnings + 1))
fi

if command -v redis-cli >/dev/null 2>&1; then
    if redis-cli ping >/dev/null 2>&1; then
        echo_info "Redis 已安装并运行"
    else
        echo_warn "Redis 已安装但未运行"
        warnings=$((warnings + 1))
    fi
else
    echo_warn "Redis CLI 未找到（可选，用于会话共享）"
    warnings=$((warnings + 1))
fi

echo ""

# 2. 检查项目文件
echo_step "2. 检查项目文件..."

if [ -f "$PROJECT_DIR/pom.xml" ]; then
    echo_info "pom.xml 存在"
else
    echo_error "pom.xml 不存在"
    failed_checks=$((failed_checks + 1))
fi

if [ -f "$PROJECT_DIR/src/main/resources/application.yml" ]; then
    echo_info "application.yml 存在"
else
    echo_error "application.yml 不存在"
    failed_checks=$((failed_checks + 1))
fi

if [ -f "$PROJECT_DIR/src/main/resources/application-multi-instance.yml" ]; then
    echo_info "application-multi-instance.yml 存在"
else
    echo_error "application-multi-instance.yml 不存在"
    failed_checks=$((failed_checks + 1))
fi

if [ -f "$PROJECT_DIR/nginx/nginx.conf" ]; then
    echo_info "nginx.conf 存在"
else
    echo_warn "nginx.conf 不存在（可选）"
    warnings=$((warnings + 1))
fi

if [ -f "$PROJECT_DIR/scripts/start-instances.sh" ]; then
    if [ -x "$PROJECT_DIR/scripts/start-instances.sh" ]; then
        echo_info "start-instances.sh 存在且可执行"
    else
        echo_warn "start-instances.sh 存在但不可执行，正在修复..."
        chmod +x "$PROJECT_DIR/scripts/start-instances.sh"
        echo_info "已修复执行权限"
    fi
else
    echo_error "start-instances.sh 不存在"
    failed_checks=$((failed_checks + 1))
fi

echo ""

# 3. 检查端口可用性
echo_step "3. 检查端口可用性..."

check_port_available() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo_warn "端口 $port 已被占用"
        return 1
    else
        echo_info "端口 $port 可用"
        return 0
    fi
}

check_port_available 8051 || warnings=$((warnings + 1))
check_port_available 8052 || warnings=$((warnings + 1))
check_port_available 8053 || warnings=$((warnings + 1))
check_port_available 80 || echo_warn "端口 80 已被占用（Nginx 需要此端口）" && warnings=$((warnings + 1))

echo ""

# 4. 检查数据库连接
echo_step "4. 检查数据库配置..."

if grep -q "jdbc:mysql" "$PROJECT_DIR/src/main/resources/application.yml" 2>/dev/null; then
    echo_info "数据库配置已设置"
    db_url=$(grep -E "url:\s*jdbc:mysql" "$PROJECT_DIR/src/main/resources/application.yml" | head -1 | sed 's/.*url:\s*//' | sed 's/\s*$//')
    if [ -n "$db_url" ]; then
        echo_info "  数据库 URL: $db_url"
    fi
else
    echo_warn "数据库配置未找到"
    warnings=$((warnings + 1))
fi

echo ""

# 5. 检查 Redis 配置
echo_step "5. 检查 Redis 配置..."

if grep -q "mcp.session.redis" "$PROJECT_DIR/src/main/resources/application.yml" 2>/dev/null || \
   grep -q "mcp.session.redis" "$PROJECT_DIR/src/main/resources/application-multi-instance.yml" 2>/dev/null; then
    echo_info "Redis 配置已设置"
    redis_host=$(grep -A 10 "redis:" "$PROJECT_DIR/src/main/resources/application-multi-instance.yml" 2>/dev/null | grep -E "^\s+host:" | head -1 | sed 's/.*host:\s*//' | sed 's/\s*$//' || echo "localhost")
    redis_port=$(grep -A 10 "redis:" "$PROJECT_DIR/src/main/resources/application-multi-instance.yml" 2>/dev/null | grep -E "^\s+port:" | head -1 | sed 's/.*port:\s*//' | sed 's/\s*$//' || echo "6379")
    echo_info "  Redis 地址: ${redis_host}:${redis_port}"
else
    echo_warn "Redis 配置未找到"
    warnings=$((warnings + 1))
fi

echo ""

# 6. 检查编译状态
echo_step "6. 检查编译状态..."

if [ -d "$PROJECT_DIR/target/classes" ]; then
    echo_info "项目已编译"
    if [ "$PROJECT_DIR/target/classes" -nt "$PROJECT_DIR/pom.xml" ]; then
        echo_info "  编译文件是最新的"
    else
        echo_warn "  编译文件可能过期，建议重新编译: mvn clean compile"
        warnings=$((warnings + 1))
    fi
else
    echo_warn "项目未编译，建议先编译: mvn clean compile"
    warnings=$((warnings + 1))
fi

echo ""

# 7. 检查目录结构
echo_step "7. 检查目录结构..."

mkdir -p "$PROJECT_DIR/logs" "$PROJECT_DIR/pids"
echo_info "日志目录: $PROJECT_DIR/logs"
echo_info "PID 目录: $PROJECT_DIR/pids"

echo ""

# 8. 检查 Nginx 配置（如果 Nginx 已安装）
if command -v nginx >/dev/null 2>&1; then
    echo_step "8. 检查 Nginx 配置..."
    
    nginx_conf="$PROJECT_DIR/nginx/nginx.conf"
    if [ -f "$nginx_conf" ]; then
        # 尝试测试配置（需要 root 权限，可能失败）
        if sudo nginx -t -c "$nginx_conf" >/dev/null 2>&1; then
            echo_info "Nginx 配置语法正确"
        else
            echo_warn "无法验证 Nginx 配置（可能需要 root 权限或配置文件路径不同）"
            warnings=$((warnings + 1))
        fi
    else
        echo_warn "Nginx 配置文件不存在"
        warnings=$((warnings + 1))
    fi
    echo ""
fi

# 总结
echo "=========================================="
echo "  检查完成"
echo "=========================================="
echo ""

if [ $failed_checks -eq 0 ]; then
    echo_info "所有必要检查通过！"
    if [ $warnings -gt 0 ]; then
        echo_warn "有 $warnings 个警告（不影响部署，但建议处理）"
    fi
    echo ""
    echo "可以开始部署："
    echo "  ./scripts/start-instances.sh start"
    exit 0
else
    echo_error "有 $failed_checks 个错误需要修复"
    if [ $warnings -gt 0 ]; then
        echo_warn "还有 $warnings 个警告"
    fi
    echo ""
    echo "请修复错误后重试"
    exit 1
fi

