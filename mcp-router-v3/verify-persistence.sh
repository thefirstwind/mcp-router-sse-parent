#!/bin/bash
#
# MCP Router V3 - 持久化功能验证脚本
# 用于快速检查应用状态和数据持久化情况
#

set -e

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo "=========================================="
echo "   MCP Router V3 - 持久化验证"
echo "=========================================="
echo ""

# 1. 检查应用进程
echo -e "${BLUE}📍 检查应用进程...${NC}"
if ps aux | grep -v grep | grep "mcp-router-v3" > /dev/null; then
    PID=$(ps aux | grep -v grep | grep "mcp-router-v3" | awk '{print $2}')
    echo -e "${GREEN}✅ 应用正在运行 (PID: $PID)${NC}"
else
    echo -e "${RED}❌ 应用未运行${NC}"
    exit 1
fi
echo ""

# 2. 检查健康状态
echo -e "${BLUE}📍 检查应用健康状态...${NC}"
HEALTH=$(curl -s http://localhost:8052/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
if [ "$HEALTH" = "UP" ]; then
    echo -e "${GREEN}✅ 应用健康状态: UP${NC}"
else
    echo -e "${RED}❌ 应用健康状态: $HEALTH${NC}"
    exit 1
fi
echo ""

# 3. 检查最近的日志
echo -e "${BLUE}📍 检查最近的持久化日志...${NC}"
if tail -100 app.log | grep -q "Server persisted to database"; then
    echo -e "${GREEN}✅ 发现持久化成功日志${NC}"
    RECENT_COUNT=$(tail -100 app.log | grep "Server persisted to database" | wc -l | tr -d ' ')
    echo -e "   最近 100 行日志中有 ${YELLOW}${RECENT_COUNT}${NC} 条持久化记录"
else
    echo -e "${YELLOW}⚠️  未发现最近的持久化日志${NC}"
fi
echo ""

# 4. 检查 MyBatis 警告
echo -e "${BLUE}📍 检查 MyBatis 警告...${NC}"
if tail -500 app.log | grep -q "VALUES() is deprecated"; then
    echo -e "${RED}❌ 发现 VALUES() 弃用警告${NC}"
    exit 1
else
    echo -e "${GREEN}✅ 无 MyBatis VALUES() 警告${NC}"
fi
echo ""

# 5. 数据库连接验证
echo -e "${BLUE}📍 验证数据库连接和数据...${NC}"

# 数据库配置
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="mcp_user"
DB_PASS="mcp_user"
DB_NAME="mcp_bridge"

# 检查数据库连接
if mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} -e "SELECT 1" 2>/dev/null > /dev/null; then
    echo -e "${GREEN}✅ 数据库连接成功${NC}"
else
    echo -e "${RED}❌ 数据库连接失败${NC}"
    exit 1
fi

# 获取统计信息
TOTAL=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} -N -e "SELECT COUNT(*) FROM mcp_servers WHERE deleted_at IS NULL" 2>/dev/null)
HEALTHY=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} -N -e "SELECT COUNT(*) FROM mcp_servers WHERE deleted_at IS NULL AND healthy = 1" 2>/dev/null)
ENABLED=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} -N -e "SELECT COUNT(*) FROM mcp_servers WHERE deleted_at IS NULL AND enabled = 1" 2>/dev/null)
EPHEMERAL=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} -N -e "SELECT COUNT(*) FROM mcp_servers WHERE deleted_at IS NULL AND ephemeral = 1" 2>/dev/null)

echo ""
echo -e "${BLUE}📊 数据库统计信息${NC}"
echo "----------------------------------------"
echo -e "- 总记录数: ${YELLOW}${TOTAL}${NC}"
echo -e "- 健康实例: ${GREEN}${HEALTHY}${NC}"
echo -e "- 启用实例: ${YELLOW}${ENABLED}${NC}"
echo -e "- 临时节点: ${YELLOW}${EPHEMERAL}${NC}"
echo ""

# 6. 显示最新的服务实例
echo -e "${BLUE}📍 最新的服务实例 (最近 5 条)${NC}"
echo "----------------------------------------"

mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} -t -e "
SELECT 
    server_name as '服务名', 
    CONCAT(host, ':', port) as '端点',
    CASE WHEN healthy = 1 THEN '✅' ELSE '❌' END as '健康',
    CASE WHEN ephemeral = 1 THEN '临时' ELSE '持久' END as '类型',
    DATE_FORMAT(updated_at, '%m-%d %H:%i:%s') as '最后更新'
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC
LIMIT 5;
" 2>/dev/null

echo ""
echo "=========================================="
echo -e "${GREEN}          验证完成 ✅${NC}"
echo "=========================================="
echo ""


