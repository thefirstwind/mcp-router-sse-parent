#!/bin/bash
# SSE连接测试脚本（Shell版本）

BASE_URL="${1:-http://127.0.0.1:8052}"
ENDPOINT="${BASE_URL}/sse/mcp-server-v6"
DURATION="${2:-600}"  # 默认10分钟

echo "=========================================="
echo "SSE连接测试"
echo "=========================================="
echo "端点: ${ENDPOINT}"
echo "持续时间: ${DURATION}秒 ($(($DURATION / 60))分钟)"
echo "开始时间: $(date)"
echo "=========================================="
echo ""

START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION))

# 使用curl测试SSE连接
curl -N -H "Accept: text/event-stream" \
     -H "Cache-Control: no-cache" \
     "${ENDPOINT}" \
     2>&1 | while IFS= read -r line; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    # 显示收到的数据
    if [[ -n "$line" ]]; then
        echo "[${ELAPSED}s] $line"
        
        # 检测心跳
        if echo "$line" | grep -q "heartbeat"; then
            echo "  💓 收到心跳"
        fi
        
        # 检测连接事件
        if echo "$line" | grep -q "connected"; then
            echo "  🔌 连接确认"
        fi
    fi
    
    # 检查是否达到目标时间
    if [ $CURRENT_TIME -ge $END_TIME ]; then
        echo ""
        echo "=========================================="
        echo "✅ 测试完成: 成功保持连接 ${DURATION}秒"
        echo "结束时间: $(date)"
        echo "=========================================="
        exit 0
    fi
done

EXIT_CODE=$?
CURRENT_TIME=$(date +%s)
ELAPSED=$((CURRENT_TIME - START_TIME))

echo ""
echo "=========================================="
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ 测试通过: 连接保持 ${ELAPSED}秒"
else
    echo "❌ 测试失败: 连接在 ${ELAPSED}秒后断开"
fi
echo "结束时间: $(date)"
echo "=========================================="

exit $EXIT_CODE

