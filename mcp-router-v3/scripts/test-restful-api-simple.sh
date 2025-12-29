#!/bin/bash

# 简单的 RESTful 分页接口测试脚本（不需要jq）

BASE_URL="${1:-http://localhost:8080}"
API_BASE="${BASE_URL}/api/restful-requests"

echo "=========================================="
echo "RESTful 请求列表分页接口简单测试"
echo "=========================================="
echo "API Base URL: ${API_BASE}"
echo ""

# 测试1: 首次查询
echo "测试1: 首次查询（limit=10, hours=24）"
echo "请求: GET ${API_BASE}?hours=24&limit=10"
RESPONSE1=$(curl -s "${API_BASE}?hours=24&limit=10")
echo "响应长度: ${#RESPONSE1} 字符"
echo "响应预览（前500字符）:"
echo "$RESPONSE1" | head -c 500
echo ""
echo ""

# 检查是否有数据
if echo "$RESPONSE1" | grep -q '"id"'; then
    echo "✅ 响应包含数据"
    
    # 提取第一条和最后一条的startTime（简单提取）
    FIRST_TIME=$(echo "$RESPONSE1" | grep -o '"startTime":"[^"]*"' | head -1 | cut -d'"' -f4)
    LAST_TIME=$(echo "$RESPONSE1" | grep -o '"startTime":"[^"]*"' | tail -1 | cut -d'"' -f4)
    
    echo "第一条记录时间: ${FIRST_TIME}"
    echo "最后一条记录时间: ${LAST_TIME}"
    echo ""
    
    # 测试2: 分页查询
    if [ -n "$LAST_TIME" ]; then
        echo "测试2: 分页查询（使用最后一条记录的时间）"
        echo "请求: GET ${API_BASE}?hours=24&limit=10&endTime=${LAST_TIME}"
        RESPONSE2=$(curl -s "${API_BASE}?hours=24&limit=10&endTime=${LAST_TIME}")
        echo "响应长度: ${#RESPONSE2} 字符"
        echo "响应预览（前500字符）:"
        echo "$RESPONSE2" | head -c 500
        echo ""
        echo ""
        
        if echo "$RESPONSE2" | grep -q '"id"'; then
            echo "✅ 分页查询返回数据"
            
            # 检查是否有重复的ID
            IDS1=$(echo "$RESPONSE1" | grep -o '"id":[0-9]*' | cut -d':' -f2 | sort)
            IDS2=$(echo "$RESPONSE2" | grep -o '"id":[0-9]*' | cut -d':' -f2 | sort)
            
            # 简单的重复检查
            DUPLICATES=$(comm -12 <(echo "$IDS1") <(echo "$IDS2"))
            if [ -z "$DUPLICATES" ]; then
                echo "✅ 没有发现重复的记录ID"
            else
                echo "❌ 发现重复的记录ID: $DUPLICATES"
            fi
        else
            echo "ℹ️  分页查询返回空结果（可能已到达数据末尾）"
        fi
    fi
else
    echo "⚠️  首次查询返回空结果或无数据"
    echo "请确保数据库中有足够的数据进行测试"
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
echo ""
echo "提示: 如果需要更详细的测试，请使用 test-restful-pagination.sh（需要jq）"










