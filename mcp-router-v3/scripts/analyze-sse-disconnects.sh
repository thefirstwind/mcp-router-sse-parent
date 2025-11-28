#!/bin/bash

echo "=== 分析 SSE 连接断开问题 ==="
echo ""

# 1. 检查最近的连接取消日志
echo "1. 检查最近的连接取消日志（最近 100 行）..."
if [ -d "logs" ]; then
    CONCEL_LOGS=$(grep -h "SSE connection cancelled\|connection cancelled" logs/*.log 2>/dev/null | tail -10)
    if [ -n "$CONCEL_LOGS" ]; then
        echo "   发现连接取消记录："
        echo "$CONCEL_LOGS" | while read line; do
            echo "   $line"
        done
    else
        echo "   ✅ 未发现连接取消记录"
    fi
else
    echo "   ⚠️  日志目录不存在"
fi
echo ""

# 2. 检查错误日志
echo "2. 检查 SSE 相关错误..."
if [ -d "logs" ]; then
    ERROR_LOGS=$(grep -h "SSE.*error\|SSE.*Error" logs/*.log 2>/dev/null | tail -10)
    if [ -n "$ERROR_LOGS" ]; then
        echo "   发现错误记录："
        echo "$ERROR_LOGS" | while read line; do
            echo "   $line"
        done
    else
        echo "   ✅ 未发现错误记录"
    fi
else
    echo "   ⚠️  日志目录不存在"
fi
echo ""

# 3. 统计连接建立和断开
echo "3. 统计连接建立和断开..."
if [ -d "logs" ]; then
    SUBSCRIBE_COUNT=$(grep -h "SSE connection subscribed" logs/*.log 2>/dev/null | wc -l | tr -d ' ')
    CANCEL_COUNT=$(grep -h "SSE connection cancelled" logs/*.log 2>/dev/null | wc -l | tr -d ' ')
    COMPLETE_COUNT=$(grep -h "SSE connection completed" logs/*.log 2>/dev/null | wc -l | tr -d ' ')
    ERROR_COUNT=$(grep -h "SSE connection error" logs/*.log 2>/dev/null | wc -l | tr -d ' ')
    
    echo "   连接建立: ${SUBSCRIBE_COUNT:-0}"
    echo "   连接取消: ${CANCEL_COUNT:-0}"
    echo "   连接完成: ${COMPLETE_COUNT:-0}"
    echo "   连接错误: ${ERROR_COUNT:-0}"
    
    if [ "${CANCEL_COUNT:-0}" -gt 0 ]; then
        RATIO=$(echo "scale=2; ${CANCEL_COUNT} * 100 / ${SUBSCRIBE_COUNT:-1}" | bc 2>/dev/null || echo "N/A")
        echo ""
        echo "   ⚠️  断开率: ${RATIO}%"
    fi
else
    echo "   ⚠️  日志目录不存在"
fi
echo ""

# 4. 检查心跳日志
echo "4. 检查心跳日志（最近 20 条）..."
if [ -d "logs" ]; then
    HEARTBEAT_LOGS=$(grep -h "SSE heartbeat" logs/*.log 2>/dev/null | tail -20)
    if [ -n "$HEARTBEAT_LOGS" ]; then
        echo "   最近心跳记录："
        echo "$HEARTBEAT_LOGS" | tail -5 | while read line; do
            echo "   $line"
        done
        echo "   ... (共 $(echo "$HEARTBEAT_LOGS" | wc -l | tr -d ' ') 条)"
    else
        echo "   ⚠️  未发现心跳记录（可能心跳未启用或日志级别过高）"
    fi
else
    echo "   ⚠️  日志目录不存在"
fi
echo ""

# 5. 分析断开模式
echo "5. 分析断开模式..."
if [ -d "logs" ] && [ "${CANCEL_COUNT:-0}" -gt 0 ]; then
    echo "   最近的断开时间点："
    grep -h "SSE connection cancelled" logs/*.log 2>/dev/null | tail -5 | while read line; do
        TIMESTAMP=$(echo "$line" | grep -oE "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}" | head -1)
        SESSION_ID=$(echo "$line" | grep -oE "sessionId=[a-f0-9-]+" | head -1)
        echo "   $TIMESTAMP - $SESSION_ID"
    done
else
    echo "   ✅ 未发现断开记录"
fi
echo ""

echo "=== 建议 ==="
echo ""
if [ "${CANCEL_COUNT:-0}" -gt 0 ]; then
    echo "如果断开率较高，检查："
    echo "1. 客户端超时设置"
    echo "2. Nginx 超时配置（proxy_read_timeout）"
    echo "3. 网络稳定性"
    echo "4. 应用日志中的错误信息"
else
    echo "✅ 未发现明显的连接断开问题"
    echo ""
    echo "如果仍然有断开问题，可能是："
    echo "1. 客户端主动断开（超时、重连逻辑）"
    echo "2. 网络问题"
    echo "3. 负载均衡问题（请求被路由到不同实例）"
fi
echo ""
