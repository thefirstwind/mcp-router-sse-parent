#!/bin/bash

# MCP Router V3 重启脚本
# 用于重启服务并验证持久化功能

echo "🔄 MCP Router V3 重启脚本"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

PROJECT_DIR="/Users/shine/projects.mcp-router-sse-parent/mcp-router-v3"

# 1. 停止现有进程
echo "📛 1. 停止现有的 MCP Router V3 进程..."
PID=$(ps aux | grep "McpRouterV3Application" | grep -v grep | awk '{print $2}' | head -1)
if [ -z "$PID" ]; then
    echo "   ℹ️  没有找到运行中的进程"
else
    echo "   🔍 找到进程 PID: $PID"
    kill $PID 2>/dev/null
    sleep 2
    
    # 检查是否还在运行
    if ps -p $PID > /dev/null 2>&1; then
        echo "   ⚠️  进程未响应 SIGTERM，使用 SIGKILL..."
        kill -9 $PID 2>/dev/null
        sleep 1
    fi
    
    if ps -p $PID > /dev/null 2>&1; then
        echo "   ❌ 无法停止进程，请手动处理"
        exit 1
    else
        echo "   ✅ 进程已停止"
    fi
fi
echo ""

# 2. 清理编译文件（可选）
echo "🧹 2. 清理编译文件..."
cd "$PROJECT_DIR"
mvn clean -q
echo "   ✅ 编译文件已清理"
echo ""

# 3. 重新编译
echo "🔨 3. 重新编译项目..."
mvn compile -DskipTests -q
if [ $? -eq 0 ]; then
    echo "   ✅ 编译成功"
else
    echo "   ❌ 编译失败"
    exit 1
fi
echo ""

# 4. 启动服务
echo "🚀 4. 启动 MCP Router V3..."
echo "   启动命令: mvn spring-boot:run"
echo "   日志文件: $PROJECT_DIR/logs/mcp-router-v3.log"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⏳ 正在启动服务，请稍候..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 在后台启动服务
nohup mvn spring-boot:run > /dev/null 2>&1 &
MAVEN_PID=$!
echo "   ℹ️  Maven 进程 PID: $MAVEN_PID"
echo ""

# 5. 等待服务启动
echo "⏳ 5. 等待服务启动（最多30秒）..."
COUNTER=0
MAX_WAIT=30

while [ $COUNTER -lt $MAX_WAIT ]; do
    sleep 1
    COUNTER=$((COUNTER + 1))
    
    # 检查服务是否已启动
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8052/actuator/health 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        echo "   ✅ 服务启动成功！(等待了 ${COUNTER} 秒)"
        break
    fi
    
    # 每5秒输出一次进度
    if [ $((COUNTER % 5)) -eq 0 ]; then
        echo "   ⏳ 已等待 ${COUNTER} 秒..."
    fi
done

echo ""

if [ $COUNTER -ge $MAX_WAIT ]; then
    echo "   ⚠️  服务启动超时，请检查日志"
    echo "   查看日志: tail -f $PROJECT_DIR/logs/mcp-router-v3.log"
    exit 1
fi

# 6. 验证持久化配置
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔍 6. 验证持久化功能初始化..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
sleep 2  # 等待日志写入

# 检查关键初始化日志
if grep -q "PersistenceEventPublisher" "$PROJECT_DIR/logs/mcp-router-v3.log" 2>/dev/null; then
    echo "   ✅ PersistenceEventPublisher 已初始化"
else
    echo "   ❌ PersistenceEventPublisher 未找到"
fi

if grep -q "RoutingLogBatchWriter" "$PROJECT_DIR/logs/mcp-router-v3.log" 2>/dev/null; then
    echo "   ✅ RoutingLogBatchWriter 已初始化"
else
    echo "   ❌ RoutingLogBatchWriter 未找到"
fi

if grep -q "HealthCheckRecordBatchWriter" "$PROJECT_DIR/logs/mcp-router-v3.log" 2>/dev/null; then
    echo "   ✅ HealthCheckRecordBatchWriter 已初始化"
else
    echo "   ❌ HealthCheckRecordBatchWriter 未找到"
fi

if grep -q "SqlSessionFactory" "$PROJECT_DIR/logs/mcp-router-v3.log" 2>/dev/null; then
    echo "   ✅ SqlSessionFactory 已配置"
else
    echo "   ❌ SqlSessionFactory 未找到"
fi

# 检查是否有错误
ERROR_COUNT=$(grep -c "ERROR" "$PROJECT_DIR/logs/mcp-router-v3.log" 2>/dev/null || echo "0")
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo ""
    echo "   ⚠️  发现 $ERROR_COUNT 个错误，请查看日志"
    echo "   最近的错误："
    grep "ERROR" "$PROJECT_DIR/logs/mcp-router-v3.log" | tail -3
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ MCP Router V3 重启完成！"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📊 服务状态："
echo "   - 服务地址: http://localhost:8052"
echo "   - 健康检查: http://localhost:8052/actuator/health"
echo "   - 日志文件: $PROJECT_DIR/logs/mcp-router-v3.log"
echo ""
echo "🧪 下一步："
echo "   1. 查看完整日志: tail -f $PROJECT_DIR/logs/mcp-router-v3.log"
echo "   2. 运行测试脚本: ./test-persistence.sh"
echo "   3. 检查数据库: mysql -umcp_user -pmcp_user mcp_bridge"
echo ""


