#!/bin/bash

###############################################################################
# MCP Router V3 - 持久化功能快速初始化脚本
###############################################################################
# 用途: 快速初始化数据库和用户，启用持久化功能
# 使用: ./init-persistence.sh [mysql_root_password]
###############################################################################

set -e

# 配置参数
DB_NAME="mcp_bridge"
DB_USER="mcp_user"
DB_PASSWORD="mcp_user"
MYSQL_HOST="127.0.0.1"
MYSQL_PORT="3306"
MYSQL_ROOT_PASSWORD="hot365fm"

echo "🚀 MCP Router V3 - 持久化功能快速初始化"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "数据库: $DB_NAME"
echo "用户: $DB_USER"
echo "主机: $MYSQL_HOST:$MYSQL_PORT"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 检查 MySQL 是否可用
echo "📡 检查 MySQL 连接..."
if ! mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1" &>/dev/null; then
    echo "❌ 无法连接到 MySQL，请检查:"
    echo "   1. MySQL 服务是否运行"
    echo "   2. root 密码是否正确: $MYSQL_ROOT_PASSWORD"
    echo "   3. 主机和端口是否正确: $MYSQL_HOST:$MYSQL_PORT"
    exit 1
fi
echo "✅ MySQL 连接成功"
echo ""

# 创建数据库和用户
echo "🗄️  创建数据库和用户..."
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" <<EOF
-- 创建数据库
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- 创建用户
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD';

-- 授权
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%';

FLUSH PRIVILEGES;

-- 显示数据库和用户
SHOW DATABASES LIKE '$DB_NAME';
SELECT User, Host FROM mysql.user WHERE User = '$DB_USER';
EOF

echo "✅ 数据库和用户创建成功"
echo ""

# 执行 schema.sql
echo "📋 执行数据库 Schema..."
if [ ! -f "schema.sql" ]; then
    echo "❌ schema.sql 文件不存在"
    echo "   请确保在 database 目录下执行此脚本"
    exit 1
fi

mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < schema.sql

echo "✅ Schema 执行成功"
echo ""

# 验证表创建
echo "🔍 验证表结构..."
TABLES=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW TABLES" | tail -n +2)

if [ -z "$TABLES" ]; then
    echo "❌ 没有检测到任何表"
    exit 1
fi

echo "✅ 检测到以下表:"
echo "$TABLES" | while read table; do
    echo "   ✓ $table"
done
echo ""

# 更新 application.yml
echo "📝 配置文件更新提示..."
echo ""
echo "请确保 application.yml 中包含以下配置:"
echo ""
echo "spring:"
echo "  datasource:"
echo "    url: jdbc:mysql://$MYSQL_HOST:$MYSQL_PORT/$DB_NAME?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
echo "    username: $DB_USER"
echo "    password: $DB_PASSWORD"
echo "    driver-class-name: com.mysql.cj.jdbc.Driver"
echo ""
echo "mybatis:"
echo "  mapper-locations: classpath:mapper/**/*.xml"
echo "  type-aliases-package: com.pajk.mcpbridge.persistence.entity"
echo "  configuration:"
echo "    map-underscore-to-camel-case: true"
echo ""

# 测试连接
echo "🧪 测试数据库连接..."
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "
SELECT 
    'Database' as test_item, 
    DATABASE() as value
UNION ALL
SELECT 
    'User' as test_item,
    USER() as value
UNION ALL
SELECT 
    'Tables' as test_item,
    COUNT(*) as value
FROM information_schema.tables 
WHERE table_schema = '$DB_NAME';
"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 持久化功能初始化完成！"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "下一步:"
echo "  1. 启动 MCP Router V3 应用"
echo "  2. 观察日志，确认 MyBatis 初始化成功"
echo "  3. 发送测试请求，验证路由日志持久化"
echo ""
echo "查询示例:"
echo "  mysql -u$DB_USER -p$DB_PASSWORD $DB_NAME -e 'SELECT * FROM routing_logs LIMIT 10;'"
echo ""





























