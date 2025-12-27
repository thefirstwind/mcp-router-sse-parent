#!/bin/bash

# 快速启动脚本 - 一键启动所有服务

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 检查依赖
check_dependencies() {
    echo_step "Checking dependencies..."
    
    local missing=0
    
    if ! command -v mvn >/dev/null 2>&1; then
        echo_error "Maven not found"
        missing=1
    fi
    
    if ! command -v nginx >/dev/null 2>&1; then
        echo_warn "Nginx not found (optional, but recommended)"
    fi
    
    if ! command -v redis-cli >/dev/null 2>&1; then
        echo_warn "Redis CLI not found (optional, for verification)"
    fi
    
    if [ $missing -eq 1 ]; then
        echo_error "Please install missing dependencies"
        exit 1
    fi
    
    echo_info "✅ Dependencies check passed"
}

# 检查端口
check_ports() {
    echo_step "Checking ports..."
    
    local ports=(8051 8052 8053 8071 8072 80)
    local occupied=()
    
    for port in "${ports[@]}"; do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            occupied+=($port)
        fi
    done
    
    if [ ${#occupied[@]} -gt 0 ]; then
        echo_warn "⚠️  Some ports are already in use: ${occupied[*]}"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        echo_info "✅ All ports are available"
    fi
}

# 启动 Redis（如果未运行）
start_redis() {
    echo_step "Checking Redis..."
    
    if command -v redis-cli >/dev/null 2>&1; then
        if redis-cli ping >/dev/null 2>&1; then
            echo_info "✅ Redis is running"
            return 0
        fi
    fi
    
    echo_warn "⚠️  Redis is not running. Please start Redis manually:"
    echo_warn "   brew services start redis  # macOS"
    echo_warn "   sudo systemctl start redis  # Linux"
    read -p "Press Enter to continue..."
}

# 启动 MCP Router 实例
start_routers() {
    echo_step "Starting MCP Router instances..."
    cd "$PROJECT_DIR"
    ./scripts/start-instances.sh start
}

# 启动 MCP Server 实例（可选）
start_servers() {
    echo_step "Starting MCP Server instances..."
    
    read -p "Start mcp-server-v6 instances? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo_info "Skipping MCP Server instances"
        return 0
    fi
    
    local server_dir="$PROJECT_DIR/../mcp-server-v6"
    if [ ! -d "$server_dir" ]; then
        echo_warn "⚠️  mcp-server-v6 directory not found: $server_dir"
        return 0
    fi
    
    cd "$server_dir"
    
    echo_info "Starting mcp-server-v6 instance 1 (port 8071)..."
    nohup mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8071" > /tmp/mcp-server-8071.log 2>&1 &
    echo $! > /tmp/mcp-server-8071.pid
    
    sleep 3
    
    echo_info "Starting mcp-server-v6 instance 2 (port 8072)..."
    nohup mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8072" > /tmp/mcp-server-8072.log 2>&1 &
    echo $! > /tmp/mcp-server-8072.pid
    
    echo_info "✅ MCP Server instances started"
    echo_info "   Logs: /tmp/mcp-server-8071.log, /tmp/mcp-server-8072.log"
}

# 配置 Nginx（可选）
setup_nginx() {
    echo_step "Setting up Nginx..."
    
    if ! command -v nginx >/dev/null 2>&1; then
        echo_warn "⚠️  Nginx not found, skipping Nginx setup"
        return 0
    fi
    
    read -p "Setup Nginx configuration? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo_info "Skipping Nginx setup"
        return 0
    fi
    
    local nginx_conf="$PROJECT_DIR/nginx/nginx.conf"
    local nginx_target=""
    
    if [ -d "/opt/homebrew/etc/nginx/servers" ]; then
        nginx_target="/opt/homebrew/etc/nginx/servers/mcp-bridge.conf"
    elif [ -d "/etc/nginx/conf.d" ]; then
        nginx_target="/etc/nginx/conf.d/mcp-bridge.conf"
    else
        echo_warn "⚠️  Nginx configuration directory not found"
        echo_info "Please manually copy $nginx_conf to your Nginx configuration directory"
        return 0
    fi
    
    if [ -n "$nginx_target" ]; then
        echo_info "Copying Nginx configuration to $nginx_target..."
        sudo cp "$nginx_conf" "$nginx_target"
        
        echo_info "Testing Nginx configuration..."
        if sudo nginx -t; then
            echo_info "✅ Nginx configuration is valid"
            read -p "Reload Nginx? (y/N) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                sudo nginx -s reload || sudo systemctl reload nginx
                echo_info "✅ Nginx reloaded"
            fi
        else
            echo_error "❌ Nginx configuration test failed"
        fi
    fi
}

# 验证部署
verify_deployment() {
    echo_step "Verifying deployment..."
    
    sleep 5
    
    echo_info "Checking instances..."
    ./scripts/start-instances.sh status
    
    echo ""
    echo_info "Testing endpoints..."
    for port in 8051 8052 8053; do
        if curl -s "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
            echo_info "✅ Instance on port $port is healthy"
        else
            echo_warn "⚠️  Instance on port $port is not responding"
        fi
    done
}

# 主函数
main() {
    echo ""
    echo "=========================================="
    echo "  MCP Router V3 Multi-Instance Setup"
    echo "=========================================="
    echo ""
    
    check_dependencies
    echo ""
    
    check_ports
    echo ""
    
    start_redis
    echo ""
    
    start_routers
    echo ""
    
    start_servers
    echo ""
    
    setup_nginx
    echo ""
    
    verify_deployment
    echo ""
    
    echo_info "=========================================="
    echo_info "  Setup Complete!"
    echo_info "=========================================="
    echo_info ""
    echo_info "Access points:"
    echo_info "  - Router instances: http://localhost:8051, 8052, 8053"
    echo_info "  - Nginx (if configured): http://mcp-bridge.local"
    echo_info ""
    echo_info "Management:"
    echo_info "  - Start/Stop: ./scripts/start-instances.sh {start|stop|restart|status}"
    echo_info "  - Verify: ./scripts/verify-session.sh"
    echo_info "  - Logs: logs/router-*.log"
    echo_info ""
}

main "$@"



















