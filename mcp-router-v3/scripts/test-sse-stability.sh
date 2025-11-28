#!/bin/bash

echo "=== 测试 SSE 连接稳定性 ==="
echo ""

URL="${1:-http://mcp-bridge.test/sse/mcp-server-v6}"
DURATION="${2:-120}"

echo "测试 URL: ${URL}"
echo "测试时长: ${DURATION} 秒（${DURATION}秒 = $((DURATION / 60)) 分钟）"
echo ""

START_TIME=$(date +%s)
LAST_HEARTBEAT=0
HEARTBEAT_COUNT=0
DISCONNECT_COUNT=0
DATA_COUNT=0
ERROR_COUNT=0

echo "开始测试... (按 Ctrl+C 停止)"
echo ""

# 使用 timeout 限制测试时间，并实时监控
timeout ${DURATION} curl -N -H "Accept: text/event-stream" \
    -H "Cache-Control: no-cache" \
    --max-time ${DURATION} \
    "${URL}" 2>&1 | while IFS= read -r line; do
    
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [[ "$line" =~ ^event:.*heartbeat ]]; then
        HEARTBEAT_COUNT=$((HEARTBEAT_COUNT + 1))
        TIME_SINCE_LAST=$((ELAPSED - LAST_HEARTBEAT))
        if [ $LAST_HEARTBEAT -gt 0 ] && [ $TIME_SINCE_LAST -gt 20 ]; then
            echo "[${ELAPSED}s] ⚠️  心跳间隔过长: ${TIME_SINCE_LAST} 秒（预期 15 秒）"
        fi
        LAST_HEARTBEAT=$ELAPSED
        echo "[${ELAPSED}s] 💓 心跳 #${HEARTBEAT_COUNT}"
    elif [[ "$line" =~ ^data:.*heartbeat ]]; then
        echo "[${ELAPSED}s] 💓 心跳数据: ${line:0:80}..."
    elif [[ "$line" =~ ^data: ]]; then
        DATA_COUNT=$((DATA_COUNT + 1))
        if [ $((DATA_COUNT % 10)) -eq 0 ]; then
            echo "[${ELAPSED}s] 📨 收到数据 #${DATA_COUNT}"
        fi
    elif [[ "$line" =~ ^event: ]]; then
        echo "[${ELAPSED}s] 📡 事件: ${line}"
    elif [[ "$line" =~ ^id: ]]; then
        echo "[${ELAPSED}s] 🆔 ID: ${line}"
    elif [[ "$line" =~ "curl:" ]] || [[ "$line" =~ "error" ]] || [[ "$line" =~ "timeout" ]] || [[ "$line" =~ "Connection" ]]; then
        ERROR_COUNT=$((ERROR_COUNT + 1))
        DISCONNECT_COUNT=$((DISCONNECT_COUNT + 1))
        echo "[${ELAPSED}s] ❌ 连接问题: ${line}"
    fi
done

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))

echo ""
echo "=== 测试结果 ==="
echo "总时长: ${TOTAL_TIME} 秒（预期: ${DURATION} 秒）"
echo "心跳次数: ${HEARTBEAT_COUNT}"
echo "数据消息: ${DATA_COUNT}"
echo "错误次数: ${ERROR_COUNT}"
echo "断开次数: ${DISCONNECT_COUNT}"
echo ""

if [ ${TOTAL_TIME} -lt ${DURATION} ]; then
    echo "⚠️  连接提前断开（提前 $((DURATION - TOTAL_TIME)) 秒）"
    echo ""
    echo "可能的原因："
    echo "1. 客户端超时"
    echo "2. 服务器端错误"
    echo "3. 网络问题"
    echo "4. Nginx 超时"
else
    echo "✅ 连接保持稳定"
fi

if [ ${HEARTBEAT_COUNT} -eq 0 ]; then
    echo "⚠️  未收到心跳消息（可能心跳机制未生效）"
elif [ ${HEARTBEAT_COUNT} -lt $((DURATION / 20)) ]; then
    EXPECTED=$((DURATION / 15))
    echo "⚠️  心跳次数偏少（收到 ${HEARTBEAT_COUNT}，预期约 ${EXPECTED}）"
else
    echo "✅ 心跳正常"
fi
echo ""
