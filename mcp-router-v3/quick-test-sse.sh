#!/bin/bash
# 快速SSE连接测试脚本（30秒测试）

BASE_URL="${1:-http://127.0.0.1:8052}"
ENDPOINT="${BASE_URL}/sse/mcp-server-v6"
DURATION=30  # 30秒快速测试

echo "=========================================="
echo "SSE连接快速测试（30秒）"
echo "=========================================="
echo "端点: ${ENDPOINT}"
echo "开始时间: $(date)"
echo "=========================================="
echo ""

START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION))
HEARTBEAT_COUNT=0
CONNECTED=false

# 使用curl测试SSE连接
curl -N -H "Accept: text/event-stream" \
     -H "Cache-Control: no-cache" \
     "${ENDPOINT}" \
     2>&1 | while IFS= read -r line; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    # 显示收到的数据
    if [[ -n "$line" ]]; then
        # 检测连接事件
        if echo "$line" | grep -qi "connected"; then
            CONNECTED=true
            echo "[${ELAPSED}s] ✅ 连接确认: $line"
        # 检测心跳
        elif echo "$line" | grep -qi "heartbeat"; then
            HEARTBEAT_COUNT=$((HEARTBEAT_COUNT + 1))
            echo "[${ELAPSED}s] 💓 心跳 #${HEARTBEAT_COUNT}: $line"
        else
            echo "[${ELAPSED}s] 📨 消息: $line"
        fi
    fi
    
    # 检查是否达到目标时间
    if [ $CURRENT_TIME -ge $END_TIME ]; then
        echo ""
        echo "=========================================="
        echo "✅ 测试完成: 连接保持 ${DURATION}秒"
        echo "收到心跳数: ${HEARTBEAT_COUNT}"
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
if [ $EXIT_CODE -eq 0 ] && [ $ELAPSED -ge $DURATION ]; then
    echo "✅ 测试通过: 连接保持 ${ELAPSED}秒，收到 ${HEARTBEAT_COUNT} 次心跳"
else
    echo "❌ 测试失败: 连接在 ${ELAPSED}秒后断开"
fi
echo "结束时间: $(date)"
echo "=========================================="

exit $EXIT_CODE

