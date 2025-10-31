#!/bin/bash

# 测试 MCP Router 持久化功能
# 
# 确保在运行此脚本前：
# 1. MySQL 服务已启动
# 2. 数据库已初始化（运行过 init-persistence.sh）
# 3. MCP Router V3 服务正在运行 (端口 8052)
# 4. MCP Server V6 服务正在运行 (端口 8071 或 8072)

echo "🧪 MCP Router 持久化功能测试"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 数据库配置
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_NAME="mcp_bridge"
DB_USER="mcp_user"
DB_PASS="mcp_user"

# 服务配置
ROUTER_URL="http://localhost:8052"
SERVICE_NAME="mcp-server-v6"

echo "📊 1. 检查数据库连接..."
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT 1;" >/dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ 数据库连接成功"
else
    echo "❌ 数据库连接失败，请检查 MySQL 服务和配置"
    exit 1
fi
echo ""

echo "📊 2. 清空测试数据（可选）..."
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
TRUNCATE TABLE routing_logs;
TRUNCATE TABLE health_check_records;
DELETE FROM mcp_servers WHERE server_key LIKE '%$SERVICE_NAME%';
EOF
echo "✅ 测试数据已清空"
echo ""

echo "📊 3. 检查初始状态..."
echo "   路由日志数量："
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT COUNT(*) as count FROM routing_logs;" 2>/dev/null | tail -n 1
echo "   健康检查记录数量："
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT COUNT(*) as count FROM health_check_records;" 2>/dev/null | tail -n 1
echo "   MCP服务器数量："
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT COUNT(*) as count FROM mcp_servers;" 2>/dev/null | tail -n 1
echo ""

echo "📊 4. 测试工具调用路由（触发持久化）..."
echo "   调用 tools/call 接口..."
RESPONSE=$(curl -s --location "${ROUTER_URL}/mcp/router/route/${SERVICE_NAME}" \
    --header 'Content-Type: application/json' \
    --data '{
        "id": "test-1",
        "method": "tools/call",
        "params": {
          "name": "getPersonById",
          "arguments": { "id": 1 }
        }
    }')

if [ $? -eq 0 ]; then
    echo "✅ 路由请求发送成功"
    echo "   响应: $RESPONSE"
else
    echo "❌ 路由请求失败"
fi
echo ""

echo "📊 5. 测试工具列表查询..."
echo "   调用 tools 列表接口..."
RESPONSE2=$(curl -s --location "${ROUTER_URL}/mcp/router/tools/${SERVICE_NAME}")
if [ $? -eq 0 ]; then
    echo "✅ 工具列表请求发送成功"
    echo "   响应前100字符: ${RESPONSE2:0:100}..."
else
    echo "❌ 工具列表请求失败"
fi
echo ""

echo "⏳ 等待 3 秒，让异步批量写入完成..."
sleep 3
echo ""

echo "📊 6. 验证数据持久化结果..."
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 路由日志 (routing_logs)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
SELECT 
    request_id,
    service_name,
    tool_name,
    is_success,
    duration_ms,
    DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') as start_time
FROM routing_logs 
ORDER BY start_time DESC 
LIMIT 5;
EOF

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🏥 健康检查记录 (health_check_records)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
SELECT 
    server_key,
    status,
    response_time_ms,
    DATE_FORMAT(check_time, '%Y-%m-%d %H:%i:%s') as check_time
FROM health_check_records 
ORDER BY check_time DESC 
LIMIT 5;
EOF

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🖥️  MCP 服务器 (mcp_servers)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
SELECT 
    server_key,
    server_name,
    host,
    port,
    status,
    DATE_FORMAT(first_registered_at, '%Y-%m-%d %H:%i:%s') as first_registered,
    DATE_FORMAT(last_heartbeat, '%Y-%m-%d %H:%i:%s') as last_heartbeat
FROM mcp_servers 
WHERE server_key LIKE '%$SERVICE_NAME%'
ORDER BY first_registered_at DESC;
EOF

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📈 统计汇总"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
SELECT 
    '路由日志' as 表名, 
    COUNT(*) as 记录数 
FROM routing_logs
UNION ALL
SELECT 
    '健康检查记录', 
    COUNT(*) 
FROM health_check_records
UNION ALL
SELECT 
    'MCP服务器', 
    COUNT(*) 
FROM mcp_servers;
EOF

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 持久化功能测试完成！"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💡 提示："
echo "   - 如果看到数据，说明持久化功能正常工作"
echo "   - 如果没有数据，请检查："
echo "     1. application.yml 中 mcp.persistence.enabled=true"
echo "     2. MCP Router 启动日志是否有持久化相关的初始化信息"
echo "     3. 是否有任何错误日志"
echo ""


