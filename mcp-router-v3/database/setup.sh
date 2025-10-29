#!/bin/bash

# MCP Bridge 数据库初始化脚本
# 使用方法: ./setup.sh

echo "🚀 开始初始化 MCP Bridge 数据库..."

# 数据库配置
DB_HOST="localhost"
DB_PORT="3306"
DB_USER="root"
DB_PASSWORD="hot365fm"
DB_NAME="mcp-bridge"

# 检查 MySQL 是否运行
echo "📋 检查 MySQL 服务状态..."
if ! command -v mysql &> /dev/null; then
    echo "❌ MySQL 客户端未安装，请先安装 MySQL"
    exit 1
fi

# 测试数据库连接
echo "🔗 测试数据库连接..."
mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} -e "SELECT 1;" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ 无法连接到 MySQL 数据库，请检查连接配置"
    echo "   主机: ${DB_HOST}:${DB_PORT}"
    echo "   用户: ${DB_USER}"
    echo "   密码: ${DB_PASSWORD}"
    exit 1
fi

echo "✅ 数据库连接成功"

# 创建数据库
echo "📦 创建数据库 ${DB_NAME}..."
mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} < init.sql
if [ $? -eq 0 ]; then
    echo "✅ 数据库创建成功"
else
    echo "❌ 数据库创建失败"
    exit 1
fi

# 创建表结构
echo "🏗️  创建表结构..."
mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} < schema.sql
if [ $? -eq 0 ]; then
    echo "✅ 表结构创建成功"
else
    echo "❌ 表结构创建失败"
    exit 1
fi

# 验证表创建
echo "🔍 验证表创建..."
TABLES=$(mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASSWORD} -D${DB_NAME} -e "SHOW TABLES;" -s)
echo "创建的表:"
echo "${TABLES}" | while read table; do
    echo "  ✓ ${table}"
done

# 检查表数量
TABLE_COUNT=$(echo "${TABLES}" | wc -l)
EXPECTED_COUNT=5  # mcp_servers, health_check_records, routing_logs, mcp_tools, system_config

if [ ${TABLE_COUNT} -eq ${EXPECTED_COUNT} ]; then
    echo "✅ 所有表创建成功 (${TABLE_COUNT}/${EXPECTED_COUNT})"
else
    echo "⚠️  表数量不匹配 (${TABLE_COUNT}/${EXPECTED_COUNT})"
fi

echo ""
echo "🎉 MCP Bridge 数据库初始化完成！"
echo ""
echo "📊 数据库信息:"
echo "   数据库名: ${DB_NAME}"
echo "   主机地址: ${DB_HOST}:${DB_PORT}"
echo "   用户名: ${DB_USER}"
echo ""
echo "🔧 应用配置:"
echo "   spring.datasource.url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
echo "   spring.datasource.username: ${DB_USER}"
echo "   spring.datasource.password: ${DB_PASSWORD}"
echo ""
echo "✨ 现在可以启动 MCP Router V3 应用了！"