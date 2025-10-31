#!/bin/bash

# ============================================================================
# MCP Router V3 数据库一键安装脚本
# ============================================================================
# 使用方法: 
#   ./setup.sh              # 使用默认配置
#   ./setup.sh mypassword   # 指定密码
# ============================================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 数据库配置
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${1:-your_password}"  # 从参数获取密码
DB_NAME="mcp_bridge"

echo "🚀 MCP Router V3 数据库初始化"
echo "================================"

# 检查 MySQL
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}❌ MySQL 客户端未安装${NC}"
    exit 1
fi

# 测试连接
echo -n "🔗 测试数据库连接... "
if mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} -e "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    echo -e "${RED}❌ 无法连接到 MySQL${NC}"
    echo "   主机: ${DB_HOST}:${DB_PORT}"
    echo "   用户: ${DB_USER}"
    echo ""
    echo "💡 提示: 运行 ./setup.sh your_password"
    exit 1
fi

# 创建数据库
echo -n "📦 创建数据库 ${DB_NAME}... "
mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} <<EOF > /dev/null 2>&1
CREATE DATABASE IF NOT EXISTS ${DB_NAME} 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;
EOF
echo -e "${GREEN}✓${NC}"

# 导入schema
echo -n "🏗️  创建表结构... "
mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < schema.sql 2>&1 | grep -v "Warning" || true
echo -e "${GREEN}✓${NC}"

# 验证
echo ""
echo "🔍 验证结果:"
TABLES=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} -D${DB_NAME} -e "SHOW TABLES;" -s 2>/dev/null)
TABLE_COUNT=$(echo "${TABLES}" | wc -l | tr -d ' ')

echo "   创建了 ${TABLE_COUNT} 张表:"
echo "${TABLES}" | while read table; do
    [ -n "$table" ] && echo "     • ${table}"
done

# 检查分区
PARTITION_COUNT=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} -D${DB_NAME} -e \
    "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA='${DB_NAME}' AND PARTITION_NAME IS NOT NULL;" -s 2>/dev/null)
echo "   创建了 ${PARTITION_COUNT} 个分区"

echo ""
echo -e "${GREEN}🎉 数据库初始化完成！${NC}"
echo ""
echo "📝 应用配置 (application.yml):"
echo "-----------------------------------"
echo "spring:"
echo "  datasource:"
echo "    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true"
echo "    username: ${DB_USER}"
echo "    password: ${DB_PASSWORD}"
echo ""
echo -e "${GREEN}✨ 现在可以启动应用了: mvn spring-boot:run${NC}"
