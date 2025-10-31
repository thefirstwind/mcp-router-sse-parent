#!/bin/bash

# 健康检查数据修复脚本
# 用途: 修正数据库中的健康检查数据准确性问题
# 作者: MCP Router Team
# 日期: 2025-10-30

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 数据库配置
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="mcp_user"
DB_PASS="mcp_user"
DB_NAME="mcp_bridge"

# MySQL 命令前缀
MYSQL_CMD="mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME}"

echo -e "${BLUE}"
cat << 'EOF'
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║         健康检查数据修复脚本 v1.0.0                            ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

echo -e "${YELLOW}⚠️  警告: 此脚本将修改数据库中的健康检查数据${NC}"
echo ""

# 步骤 1: 显示当前问题
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 步骤 1/5: 显示当前问题状态${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo "【问题服务】"
$MYSQL_CMD -e "
SELECT 
    server_name, 
    CONCAT(host, ':', port) AS endpoint,
    healthy,
    last_health_check,
    updated_at
FROM mcp_servers 
WHERE server_name IN ('cf-server', 'mcp-server-v2-20250718') 
AND deleted_at IS NULL;" | column -t

echo ""
echo "【统计信息】"
$MYSQL_CMD -e "
SELECT 
    COUNT(*) as total_servers,
    SUM(CASE WHEN healthy = 1 THEN 1 ELSE 0 END) as marked_healthy,
    SUM(CASE WHEN healthy = 0 THEN 1 ELSE 0 END) as marked_unhealthy,
    SUM(CASE WHEN last_health_check IS NULL THEN 1 ELSE 0 END) as no_health_check
FROM mcp_servers 
WHERE deleted_at IS NULL;" | column -t

echo ""
read -p "$(echo -e ${YELLOW}继续修复？ [y/N]: ${NC})" -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}❌ 用户取消操作${NC}"
    exit 1
fi

# 步骤 2: 备份当前数据
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}💾 步骤 2/5: 备份当前数据${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

BACKUP_FILE="mcp_servers_backup_$(date +%Y%m%d_%H%M%S).sql"
echo "📦 备份文件: ${BACKUP_FILE}"

mysqldump -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} \
    ${DB_NAME} mcp_servers > "${BACKUP_FILE}" 2>/dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 数据备份成功${NC}"
    ls -lh "${BACKUP_FILE}"
else
    echo -e "${RED}❌ 数据备份失败${NC}"
    exit 1
fi

# 步骤 3: 实际健康检查验证
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔍 步骤 3/5: 实际健康检查验证${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# 检查 cf-server
echo -n "检查 cf-server (127.0.0.1:8899)... "
if curl -s --connect-timeout 2 http://127.0.0.1:8899/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 健康${NC}"
    CF_SERVER_HEALTHY=1
else
    echo -e "${RED}❌ 不健康${NC}"
    CF_SERVER_HEALTHY=0
fi

# 检查 mcp-server-v2-20250718
echo -n "检查 mcp-server-v2-20250718 (127.0.0.1:8090)... "
if curl -s --connect-timeout 2 http://127.0.0.1:8090/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 健康${NC}"
    MCP_V2_HEALTHY=1
else
    echo -e "${RED}❌ 不健康${NC}"
    MCP_V2_HEALTHY=0
fi

# 步骤 4: 执行数据修正
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🛠️  步骤 4/5: 执行数据修正${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# 4.1: 为所有服务设置初始健康检查时间
echo "📝 4.1 为所有 last_health_check=NULL 的服务设置初始检查时间..."
ROWS_UPDATED=$($MYSQL_CMD -N -e "
UPDATE mcp_servers 
SET last_health_check = updated_at 
WHERE last_health_check IS NULL 
AND deleted_at IS NULL;
SELECT ROW_COUNT();")

echo -e "${GREEN}✅ 已更新 ${ROWS_UPDATED} 条记录${NC}"

# 4.2: 根据实际检查结果更新健康状态
echo ""
echo "📝 4.2 根据实际健康检查结果更新服务状态..."

# 更新 cf-server
$MYSQL_CMD -e "
UPDATE mcp_servers 
SET healthy = ${CF_SERVER_HEALTHY}, 
    last_health_check = NOW(),
    updated_at = NOW() 
WHERE server_name = 'cf-server' 
AND deleted_at IS NULL;"

echo -e "${GREEN}✅ cf-server 状态已更新为: healthy=${CF_SERVER_HEALTHY}${NC}"

# 更新 mcp-server-v2-20250718
$MYSQL_CMD -e "
UPDATE mcp_servers 
SET healthy = ${MCP_V2_HEALTHY}, 
    last_health_check = NOW(),
    updated_at = NOW() 
WHERE server_name = 'mcp-server-v2-20250718' 
AND deleted_at IS NULL;"

echo -e "${GREEN}✅ mcp-server-v2-20250718 状态已更新为: healthy=${MCP_V2_HEALTHY}${NC}"

# 步骤 5: 验证修复结果
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}✅ 步骤 5/5: 验证修复结果${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo "【修复后的服务状态】"
$MYSQL_CMD -e "
SELECT 
    server_name, 
    CONCAT(host, ':', port) AS endpoint,
    CASE WHEN healthy = 1 THEN '✅' ELSE '❌' END AS status,
    healthy,
    last_health_check,
    updated_at
FROM mcp_servers 
WHERE server_name IN ('cf-server', 'mcp-server-v2-20250718') 
AND deleted_at IS NULL;" | column -t

echo ""
echo "【修复后的统计信息】"
$MYSQL_CMD -e "
SELECT 
    COUNT(*) as total_servers,
    SUM(CASE WHEN healthy = 1 THEN 1 ELSE 0 END) as marked_healthy,
    SUM(CASE WHEN healthy = 0 THEN 1 ELSE 0 END) as marked_unhealthy,
    SUM(CASE WHEN last_health_check IS NULL THEN 1 ELSE 0 END) as no_health_check
FROM mcp_servers 
WHERE deleted_at IS NULL;" | column -t

echo ""
echo "【所有服务概览】"
$MYSQL_CMD -e "
SELECT 
    server_name,
    CONCAT(host, ':', port) AS endpoint,
    CASE WHEN healthy = 1 THEN '✅' ELSE '❌' END AS status,
    CASE WHEN ephemeral = 1 THEN '临时' ELSE '持久' END AS type,
    DATE_FORMAT(last_health_check, '%H:%i:%s') AS last_check,
    DATE_FORMAT(updated_at, '%H:%i:%s') AS updated
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC;" | column -t

# 完成
echo ""
echo -e "${GREEN}"
cat << 'EOF'
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║              ✅ 数据修复完成！                                 ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

echo "📋 修复总结:"
echo "  - ✅ 数据已备份到: ${BACKUP_FILE}"
echo "  - ✅ 所有服务的 last_health_check 已设置"
echo "  - ✅ cf-server 健康状态: $([ $CF_SERVER_HEALTHY -eq 1 ] && echo '✅ 健康' || echo '❌ 不健康')"
echo "  - ✅ mcp-server-v2-20250718 健康状态: $([ $MCP_V2_HEALTHY -eq 1 ] && echo '✅ 健康' || echo '❌ 不健康')"
echo ""

echo "📚 后续建议:"
echo "  1. 查看详细分析: cat HEALTH_CHECK_DATA_ACCURACY_ISSUE.md"
echo "  2. 实施代码修复: 在 McpServer.fromRegistration() 中添加 lastHealthCheck"
echo "  3. 监控超时检查: 观察 checkAndMarkTimeoutServers() 是否正常工作"
echo "  4. 考虑启用定期健康检查"
echo ""

echo "🔄 如需回滚，请执行:"
echo "  mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} < ${BACKUP_FILE}"
echo ""


