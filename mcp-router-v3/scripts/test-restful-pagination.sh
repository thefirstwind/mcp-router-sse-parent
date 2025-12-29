#!/bin/bash

# RESTful 请求列表分页接口测试脚本
# 用于验证后端分页功能是否正确

BASE_URL="${1:-http://localhost:8080}"
API_BASE="${BASE_URL}/api/restful-requests"

echo "=========================================="
echo "RESTful 请求列表分页接口测试"
echo "=========================================="
echo "API Base URL: ${API_BASE}"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试1: 首次查询（获取前10条）
echo -e "${YELLOW}测试1: 首次查询（limit=10, hours=24）${NC}"
RESPONSE1=$(curl -s "${API_BASE}?hours=24&limit=10")
echo "响应:"
echo "$RESPONSE1" | jq '.' 2>/dev/null || echo "$RESPONSE1"
echo ""

# 提取第一条和最后一条记录的时间
FIRST_TIME=$(echo "$RESPONSE1" | jq -r '.[0].startTime' 2>/dev/null)
LAST_TIME=$(echo "$RESPONSE1" | jq -r '.[-1].startTime' 2>/dev/null)
COUNT=$(echo "$RESPONSE1" | jq 'length' 2>/dev/null)

echo "统计信息:"
echo "  返回记录数: ${COUNT}"
echo "  第一条记录时间: ${FIRST_TIME}"
echo "  最后一条记录时间: ${LAST_TIME}"
echo ""

if [ "$COUNT" -lt 10 ]; then
    echo -e "${YELLOW}⚠️  警告: 返回的记录数少于10条，可能没有足够的数据进行分页测试${NC}"
    echo ""
fi

# 测试2: 使用最后一条记录的时间作为endTime进行分页查询
if [ -n "$LAST_TIME" ] && [ "$LAST_TIME" != "null" ]; then
    echo -e "${YELLOW}测试2: 分页查询（使用最后一条记录的时间作为endTime）${NC}"
    echo "endTime参数: ${LAST_TIME}"
    
    # URL编码endTime
    END_TIME_ENCODED=$(echo "$LAST_TIME" | sed 's/ /%20/g')
    RESPONSE2=$(curl -s "${API_BASE}?hours=24&limit=10&endTime=${END_TIME_ENCODED}")
    
    echo "响应:"
    echo "$RESPONSE2" | jq '.' 2>/dev/null || echo "$RESPONSE2"
    echo ""
    
    COUNT2=$(echo "$RESPONSE2" | jq 'length' 2>/dev/null)
    FIRST_TIME2=$(echo "$RESPONSE2" | jq -r '.[0].startTime' 2>/dev/null)
    LAST_TIME2=$(echo "$RESPONSE2" | jq -r '.[-1].startTime' 2>/dev/null)
    
    echo "统计信息:"
    echo "  返回记录数: ${COUNT2}"
    echo "  第一条记录时间: ${FIRST_TIME2}"
    echo "  最后一条记录时间: ${LAST_TIME2}"
    echo ""
    
    # 验证分页是否正确
    echo -e "${YELLOW}验证分页逻辑:${NC}"
    
    # 检查是否有重复数据
    ALL_IDS=$(echo "$RESPONSE1" "$RESPONSE2" | jq -s '.[] | .[].id' | sort | uniq -d)
    if [ -z "$ALL_IDS" ]; then
        echo -e "${GREEN}✅ 通过: 两次查询没有重复的记录${NC}"
    else
        echo -e "${RED}❌ 失败: 发现重复的记录ID: ${ALL_IDS}${NC}"
    fi
    
    # 检查时间顺序
    if [ "$FIRST_TIME2" != "null" ] && [ "$LAST_TIME" != "null" ]; then
        # 比较时间（简单字符串比较，ISO格式可以这样比较）
        if [ "$FIRST_TIME2" \< "$LAST_TIME" ]; then
            echo -e "${GREEN}✅ 通过: 分页查询返回的数据时间早于上次查询的最后一条${NC}"
        else
            echo -e "${RED}❌ 失败: 分页查询返回的数据时间不正确${NC}"
            echo "   上次最后一条: ${LAST_TIME}"
            echo "   本次第一条: ${FIRST_TIME2}"
        fi
    fi
    
    # 检查是否连续
    if [ "$COUNT" -eq 10 ] && [ "$COUNT2" -eq 10 ]; then
        echo -e "${GREEN}✅ 通过: 两次查询都返回了10条记录（分页大小正确）${NC}"
    elif [ "$COUNT2" -lt 10 ]; then
        echo -e "${YELLOW}ℹ️  信息: 第二次查询返回${COUNT2}条记录，可能已到达数据末尾${NC}"
    fi
    
    echo ""
    
    # 测试3: 继续分页（如果还有数据）
    if [ "$COUNT2" -eq 10 ] && [ -n "$LAST_TIME2" ] && [ "$LAST_TIME2" != "null" ]; then
        echo -e "${YELLOW}测试3: 继续分页查询${NC}"
        END_TIME_ENCODED2=$(echo "$LAST_TIME2" | sed 's/ /%20/g')
        RESPONSE3=$(curl -s "${API_BASE}?hours=24&limit=10&endTime=${END_TIME_ENCODED2}")
        
        COUNT3=$(echo "$RESPONSE3" | jq 'length' 2>/dev/null)
        echo "返回记录数: ${COUNT3}"
        
        if [ "$COUNT3" -gt 0 ]; then
            FIRST_TIME3=$(echo "$RESPONSE3" | jq -r '.[0].startTime' 2>/dev/null)
            echo "第一条记录时间: ${FIRST_TIME3}"
            
            # 验证没有重复
            ALL_IDS3=$(echo "$RESPONSE1" "$RESPONSE2" "$RESPONSE3" | jq -s '.[] | .[].id' | sort | uniq -d)
            if [ -z "$ALL_IDS3" ]; then
                echo -e "${GREEN}✅ 通过: 三次查询没有重复的记录${NC}"
            else
                echo -e "${RED}❌ 失败: 发现重复的记录${NC}"
            fi
        fi
        echo ""
    fi
else
    echo -e "${RED}❌ 无法进行分页测试: 无法获取最后一条记录的时间${NC}"
fi

# 测试4: 验证limit参数
echo -e "${YELLOW}测试4: 验证limit参数（limit=5）${NC}"
RESPONSE4=$(curl -s "${API_BASE}?hours=24&limit=5")
COUNT4=$(echo "$RESPONSE4" | jq 'length' 2>/dev/null)
echo "返回记录数: ${COUNT4}"
if [ "$COUNT4" -le 5 ]; then
    echo -e "${GREEN}✅ 通过: limit参数生效${NC}"
else
    echo -e "${RED}❌ 失败: limit参数未生效，返回了${COUNT4}条记录${NC}"
fi
echo ""

# 测试5: 验证时间范围参数
echo -e "${YELLOW}测试5: 验证时间范围参数（hours=1）${NC}"
RESPONSE5=$(curl -s "${API_BASE}?hours=1&limit=10")
COUNT5=$(echo "$RESPONSE5" | jq 'length' 2>/dev/null)
echo "返回记录数: ${COUNT5}"
echo -e "${GREEN}✅ 时间范围参数测试完成${NC}"
echo ""

echo "=========================================="
echo "测试完成"
echo "=========================================="










