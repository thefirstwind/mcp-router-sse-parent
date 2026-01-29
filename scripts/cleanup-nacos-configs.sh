#!/bin/bash

# Nacos 老配置清理脚本
# 用途：识别并清理 Nacos 中过期的 MCP 配置

set -e

# Nacos 配置
NACOS_SERVER="${NACOS_SERVER:-http://127.0.0.1:8848}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数：打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 函数：列出指定组的所有配置
list_configs() {
    local group=$1
    print_info "正在查询 Nacos 配置（Group: ${group}）..."
    
    curl -s -X GET \
        "${NACOS_SERVER}/nacos/v1/cs/configs?search=accurate&dataId=&group=${group}&pageNo=1&pageSize=100" \
        -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}&tenant=${NACOS_NAMESPACE}" \
        | jq -r '.pageItems[] | "\(.dataId)|\(.group)|\(.lastModified)"'
}

# 函数：读取配置内容
get_config() {
    local dataId=$1
    local group=$2
    
    curl -s -X GET \
        "${NACOS_SERVER}/nacos/v1/cs/configs?dataId=${dataId}&group=${group}&tenant=${NACOS_NAMESPACE}" \
        -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}"
}

# 函数：删除配置
delete_config() {
    local dataId=$1
    local group=$2
    
    curl -s -X DELETE \
        "${NACOS_SERVER}/nacos/v1/cs/configs?dataId=${dataId}&group=${group}&tenant=${NACOS_NAMESPACE}" \
        -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}"
}

# 函数：备份配置
backup_config() {
    local dataId=$1
    local group=$2
    local backup_dir="nacos_config_backup_$(date +%Y%m%d_%H%M%S)"
    
    mkdir -p "${backup_dir}"
    
    local content=$(get_config "${dataId}" "${group}")
    local filename="${backup_dir}/${group}_${dataId}"
    
    echo "${content}" > "${filename}"
    print_success "已备份: ${filename}"
}

# 主函数：分析配置
analyze_configs() {
    print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    print_info "Nacos MCP 配置分析"
    print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    # 列出 mcp-tools 组的配置
    print_info "📦 MCP Tools 配置："
    echo ""
    list_configs "mcp-tools" | while IFS='|' read -r dataId group lastModified; do
        if [[ -n "$dataId" ]]; then
            # 检查是否符合命名规范
            if [[ $dataId =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[0-9]+\.[0-9]+\.[0-9]+-mcp-tools\.json$ ]]; then
                print_success "  标准格式: ${dataId} (${lastModified})"
            else
                print_warning "  ⚠️  非标准格式: ${dataId} (${lastModified})"
            fi
        fi
    done
    
    echo ""
    
    # 列出 mcp-server 组的配置
    print_info "🔧 MCP Server 配置："
    echo ""
    list_configs "mcp-server" | while IFS='|' read -r dataId group lastModified; do
        if [[ -n "$dataId" ]]; then
            if [[ $dataId =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[0-9]+\.[0-9]+\.[0-9]+-mcp-server\.json$ ]]; then
                print_success "  标准格式: ${dataId} (${lastModified})"
            else
                print_warning "  ⚠️  非标准格式: ${dataId} (${lastModified})"
            fi
        fi
    done
    
    echo ""
    
    # 列出 mcp-server-versions 组的配置
    print_info "📋 MCP Versions 配置："
    echo ""
    list_configs "mcp-server-versions" | while IFS='|' read -r dataId group lastModified; do
        if [[ -n "$dataId" ]]; then
            print_success "  ${dataId} (${lastModified})"
        fi
    done
    
    echo ""
    print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# 主函数：交互式清理
interactive_cleanup() {
    print_info "开始交互式配置清理..."
    echo ""
    
    local groups=("mcp-tools" "mcp-server" "mcp-server-versions")
    
    for group in "${groups[@]}"; do
        print_info "处理组: ${group}"
        
        list_configs "${group}" | while IFS='|' read -r dataId group lastModified; do
            if [[ -n "$dataId" ]]; then
                # 检查是否为非标准格式
                if [[ ! $dataId =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}- ]]; then
                    print_warning "发现非标准配置: ${dataId}"
                    echo ""
                    
                    read -p "是否要删除此配置？(y/N) " -n 1 -r
                    echo ""
                    
                    if [[ $REPLY =~ ^[Yy]$ ]]; then
                        # 先备份
                        backup_config "${dataId}" "${group}"
                        
                        # 再删除
                        if delete_config "${dataId}" "${group}"; then
                            print_success "已删除: ${dataId}"
                        else
                            print_error "删除失败: ${dataId}"
                        fi
                    else
                        print_info "跳过: ${dataId}"
                    fi
                    
                    echo ""
                fi
            fi
        done
    done
}

# 主函数：批量清理非标准配置
batch_cleanup() {
    print_warning "警告：此操作将删除所有非标准命名格式的配置！"
    read -p "确认继续？(yes/no) " -r
    echo ""
    
    if [[ ! $REPLY == "yes" ]]; then
        print_info "已取消操作"
        exit 0
    fi
    
    local backup_dir="nacos_config_backup_$(date +%Y%m%d_%H%M%S)"
    mkdir -p "${backup_dir}"
    print_success "创建备份目录: ${backup_dir}"
    
    local groups=("mcp-tools" "mcp-server" "mcp-server-versions")
    local deleted_count=0
    
    for group in "${groups[@]}"; do
        print_info "处理组: ${group}"
        
        list_configs "${group}" | while IFS='|' read -r dataId group lastModified; do
            if [[ -n "$dataId" ]]; then
                # 检查是否为非标准格式
                if [[ ! $dataId =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}- ]]; then
                    print_warning "删除非标准配置: ${dataId}"
                    
                    # 备份
                    local content=$(get_config "${dataId}" "${group}")
                    echo "${content}" > "${backup_dir}/${group}_${dataId}"
                    
                    # 删除
                    if delete_config "${dataId}" "${group}"; then
                        print_success "✓ 已删除并备份: ${dataId}"
                        ((deleted_count++))
                    else
                        print_error "✗ 删除失败: ${dataId}"
                    fi
                fi
            fi
        done
    done
    
    echo ""
    print_success "清理完成！共删除 ${deleted_count} 个配置"
    print_info "备份位置: ${backup_dir}"
}

# 显示使用说明
show_usage() {
    cat << EOF
Nacos MCP 配置清理工具

用法: $0 [命令]

命令:
  analyze     分析当前 Nacos 中的 MCP 配置
  interactive 交互式清理（逐个确认）
  batch       批量清理所有非标准配置（需确认）
  help        显示此帮助信息

环境变量:
  NACOS_SERVER    Nacos 服务器地址（默认: http://127.0.0.1:8848）
  NACOS_USERNAME  Nacos 用户名（默认: nacos）
  NACOS_PASSWORD  Nacos 密码（默认: nacos）
  NACOS_NAMESPACE Nacos 命名空间（默认: public）

示例:
  # 分析配置
  $0 analyze
  
  # 交互式清理
  $0 interactive
  
  # 使用自定义 Nacos 地址
  NACOS_SERVER=http://192.168.1.100:8848 $0 analyze

注意:
  - 所有删除操作都会先备份配置
  - 备份文件保存在 nacos_config_backup_* 目录中
EOF
}

# 主程序
main() {
    local command=${1:-help}
    
    # 检查依赖
    if ! command -v jq &> /dev/null; then
        print_error "需要安装 jq 工具"
        print_info "安装命令: brew install jq"
        exit 1
    fi
    
    case "$command" in
        analyze)
            analyze_configs
            ;;
        interactive)
            interactive_cleanup
            ;;
        batch)
            batch_cleanup
            ;;
        help)
            show_usage
            ;;
        *)
            print_error "未知命令: $command"
            echo ""
            show_usage
            exit 1
            ;;
    esac
}

main "$@"
