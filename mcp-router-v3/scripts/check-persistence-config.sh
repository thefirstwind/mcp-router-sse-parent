#!/bin/bash

# 持久化配置检查脚本
# 用于排查 PersistenceEventPublisher is null 问题

echo "=========================================="
echo "持久化配置检查脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 检查配置文件
echo "1. 检查 application.yml 配置:"
if [ -f "src/main/resources/application.yml" ]; then
    PERSISTENCE_ENABLED=$(grep -A 3 "mcp:" src/main/resources/application.yml | grep -A 2 "persistence:" | grep "enabled" | awk '{print $2}')
    if [ "$PERSISTENCE_ENABLED" = "true" ]; then
        echo -e "${GREEN}✅ mcp.persistence.enabled = true${NC}"
    else
        echo -e "${RED}❌ mcp.persistence.enabled = $PERSISTENCE_ENABLED (应该是 true)${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  application.yml 文件不存在${NC}"
fi

# 2. 检查环境变量
echo ""
echo "2. 检查环境变量:"
ENV_VAR=$(env | grep -i "MCP.*PERSISTENCE.*ENABLED" | head -1)
if [ -z "$ENV_VAR" ]; then
    echo -e "${GREEN}✅ 未设置相关环境变量${NC}"
else
    echo -e "${YELLOW}⚠️  发现环境变量: $ENV_VAR${NC}"
    echo "   注意：环境变量会覆盖配置文件中的设置"
fi

# 3. 检查启动日志
echo ""
echo "3. 检查启动日志:"
LOG_FILE="mcp-router-v3-verify.log"
if [ ! -f "$LOG_FILE" ]; then
    LOG_FILE="logs/application.log"
fi

if [ -f "$LOG_FILE" ]; then
    # 检查 PersistenceEventPublisher 初始化
    if grep -q "PersistenceEventPublisher initialized" "$LOG_FILE"; then
        echo -e "${GREEN}✅ PersistenceEventPublisher 已初始化${NC}"
        grep "PersistenceEventPublisher initialized" "$LOG_FILE" | tail -1
    else
        echo -e "${RED}❌ PersistenceEventPublisher 未初始化${NC}"
        echo "   说明：Bean 没有被创建，检查配置 mcp.persistence.enabled"
    fi
    
    # 检查批量写入器启动
    echo ""
    if grep -q "batch writer started successfully" "$LOG_FILE"; then
        echo -e "${GREEN}✅ 批量写入器已启动${NC}"
        grep "batch writer started successfully" "$LOG_FILE" | tail -2
    else
        echo -e "${RED}❌ 批量写入器未启动${NC}"
    fi
    
    # 检查运行时错误
    echo ""
    NULL_ERRORS=$(grep -c "PersistenceEventPublisher is null" "$LOG_FILE" 2>/dev/null || echo "0")
    if [ "$NULL_ERRORS" -gt 0 ]; then
        echo -e "${RED}❌ 发现 $NULL_ERRORS 条 'PersistenceEventPublisher is null' 错误${NC}"
        echo "   最近的错误："
        grep "PersistenceEventPublisher is null" "$LOG_FILE" | tail -3
    else
        echo -e "${GREEN}✅ 未发现 'PersistenceEventPublisher is null' 错误${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  日志文件不存在: $LOG_FILE${NC}"
fi

# 4. 检查数据库连接（如果可能）
echo ""
echo "4. 检查数据库连接:"
if command -v mysql &> /dev/null; then
    # 尝试连接数据库（需要根据实际情况修改连接信息）
    echo "   提示：可以手动执行以下 SQL 检查表是否存在："
    echo "   SELECT COUNT(*) FROM routing_logs;"
    echo "   SELECT COUNT(*) FROM health_check_records;"
else
    echo -e "${YELLOW}⚠️  mysql 命令不可用，跳过数据库检查${NC}"
fi

# 5. 提供解决建议
echo ""
echo "=========================================="
echo "解决建议:"
echo "=========================================="
echo ""
echo "如果 PersistenceEventPublisher 未初始化，请检查："
echo ""
echo "1. 配置文件 application.yml 中是否有："
echo "   mcp:"
echo "     persistence:"
echo "       enabled: true"
echo ""
echo "2. 环境变量是否覆盖了配置："
echo "   env | grep -i MCP"
echo ""
echo "3. 是否有 profile 特定配置："
echo "   cat application-*.yml"
echo ""
echo "4. 重启应用后检查启动日志："
echo "   grep -i 'PersistenceEventPublisher initialized' logs/application.log"
echo ""
echo "=========================================="




