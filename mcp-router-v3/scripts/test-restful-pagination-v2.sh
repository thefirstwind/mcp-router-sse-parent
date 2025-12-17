#!/bin/bash

# RESTful 请求列表分页接口测试脚本（使用 pageNo 和 pageSize）
# 用于验证标准分页功能是否正确

BASE_URL="${1:-http://localhost:8080}"
API_BASE="${BASE_URL}/api/restful-requests"

echo "=========================================="
echo "RESTful 请求列表分页接口测试 (pageNo/pageSize)"
echo "=========================================="
echo "API Base URL: ${API_BASE}"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试1: 第一页查询（pageNo=1, pageSize=10）
echo -e "${YELLOW}测试1: 第一页查询（pageNo=1, pageSize=10, hours=24）${NC}"
RESPONSE1=$(curl -s "${API_BASE}?hours=24&pageNo=1&pageSize=10")
echo "响应:"
echo "$RESPONSE1" | jq '.' 2>/dev/null || echo "$RESPONSE1"
echo ""

# 提取分页信息
PAGE_NO1=$(echo "$RESPONSE1" | jq -r '.pageNo' 2>/dev/null)
PAGE_SIZE1=$(echo "$RESPONSE1" | jq -r '.pageSize' 2>/dev/null)
TOTAL_COUNT=$(echo "$RESPONSE1" | jq -r '.totalCount' 2>/dev/null)
TOTAL_PAGES=$(echo "$RESPONSE1" | jq -r '.totalPages' 2>/dev/null)
DATA_COUNT1=$(echo "$RESPONSE1" | jq '.data | length' 2>/dev/null)

echo "分页信息:"
echo "  页码 (pageNo): ${PAGE_NO1}"
echo "  每页大小 (pageSize): ${PAGE_SIZE1}"
echo "  总记录数 (totalCount): ${TOTAL_COUNT}"
echo "  总页数 (totalPages): ${TOTAL_PAGES}"
echo "  当前页数据量: ${DATA_COUNT1}"
echo ""

# 验证第一页
if [ "$PAGE_NO1" = "1" ] && [ "$PAGE_SIZE1" = "10" ]; then
    echo -e "${GREEN}✅ 通过: 分页参数正确${NC}"
else
    echo -e "${RED}❌ 失败: 分页参数不正确${NC}"
fi

if [ "$DATA_COUNT1" -le 10 ]; then
    echo -e "${GREEN}✅ 通过: 返回数据量正确（<= pageSize）${NC}"
else
    echo -e "${RED}❌ 失败: 返回数据量超过 pageSize${NC}"
fi

if [ "$TOTAL_PAGES" -gt 0 ]; then
    echo -e "${GREEN}✅ 通过: 总页数计算正确${NC}"
else
    echo -e "${YELLOW}⚠️  警告: 总页数为0，可能没有数据${NC}"
fi
echo ""

# 测试2: 第二页查询
if [ "$TOTAL_PAGES" -gt 1 ]; then
    echo -e "${YELLOW}测试2: 第二页查询（pageNo=2, pageSize=10）${NC}"
    RESPONSE2=$(curl -s "${API_BASE}?hours=24&pageNo=2&pageSize=10")
    
    PAGE_NO2=$(echo "$RESPONSE2" | jq -r '.pageNo' 2>/dev/null)
    DATA_COUNT2=$(echo "$RESPONSE2" | jq '.data | length' 2>/dev/null)
    TOTAL_COUNT2=$(echo "$RESPONSE2" | jq -r '.totalCount' 2>/dev/null)
    
    echo "分页信息:"
    echo "  页码 (pageNo): ${PAGE_NO2}"
    echo "  当前页数据量: ${DATA_COUNT2}"
    echo "  总记录数: ${TOTAL_COUNT2}"
    echo ""
    
    # 验证第二页
    if [ "$PAGE_NO2" = "2" ]; then
        echo -e "${GREEN}✅ 通过: 第二页页码正确${NC}"
    else
        echo -e "${RED}❌ 失败: 第二页页码不正确${NC}"
    fi
    
    # 检查是否有重复数据
    IDS1=$(echo "$RESPONSE1" | jq -r '.data[].id' 2>/dev/null | sort)
    IDS2=$(echo "$RESPONSE2" | jq -r '.data[].id' 2>/dev/null | sort)
    DUPLICATES=$(comm -12 <(echo "$IDS1") <(echo "$IDS2"))
    
    if [ -z "$DUPLICATES" ]; then
        echo -e "${GREEN}✅ 通过: 两次查询没有重复的记录${NC}"
    else
        echo -e "${RED}❌ 失败: 发现重复的记录ID: ${DUPLICATES}${NC}"
    fi
    
    # 验证总数一致
    if [ "$TOTAL_COUNT" = "$TOTAL_COUNT2" ]; then
        echo -e "${GREEN}✅ 通过: 总记录数一致${NC}"
    else
        echo -e "${RED}❌ 失败: 总记录数不一致${NC}"
    fi
    echo ""
else
    echo -e "${YELLOW}⚠️  跳过第二页测试: 总页数 <= 1${NC}"
    echo ""
fi

# 测试3: 验证最后一页
if [ "$TOTAL_PAGES" -gt 2 ]; then
    echo -e "${YELLOW}测试3: 最后一页查询（pageNo=${TOTAL_PAGES}）${NC}"
    RESPONSE3=$(curl -s "${API_BASE}?hours=24&pageNo=${TOTAL_PAGES}&pageSize=10")
    
    PAGE_NO3=$(echo "$RESPONSE3" | jq -r '.pageNo' 2>/dev/null)
    DATA_COUNT3=$(echo "$RESPONSE3" | jq '.data | length' 2>/dev/null)
    
    echo "分页信息:"
    echo "  页码 (pageNo): ${PAGE_NO3}"
    echo "  当前页数据量: ${DATA_COUNT3}"
    echo ""
    
    if [ "$PAGE_NO3" = "$TOTAL_PAGES" ]; then
        echo -e "${GREEN}✅ 通过: 最后一页页码正确${NC}"
    else
        echo -e "${RED}❌ 失败: 最后一页页码不正确${NC}"
    fi
    
    if [ "$DATA_COUNT3" -le 10 ]; then
        echo -e "${GREEN}✅ 通过: 最后一页数据量正确${NC}"
    else
        echo -e "${RED}❌ 失败: 最后一页数据量超过 pageSize${NC}"
    fi
    echo ""
fi

# 测试4: 验证超出范围的页码
if [ "$TOTAL_PAGES" -gt 0 ]; then
    echo -e "${YELLOW}测试4: 超出范围的页码（pageNo=$((TOTAL_PAGES + 1))）${NC}"
    RESPONSE4=$(curl -s "${API_BASE}?hours=24&pageNo=$((TOTAL_PAGES + 1))&pageSize=10")
    DATA_COUNT4=$(echo "$RESPONSE4" | jq '.data | length' 2>/dev/null)
    
    if [ "$DATA_COUNT4" = "0" ]; then
        echo -e "${GREEN}✅ 通过: 超出范围的页码返回空数据${NC}"
    else
        echo -e "${YELLOW}ℹ️  信息: 超出范围的页码返回了 ${DATA_COUNT4} 条数据${NC}"
    fi
    echo ""
fi

# 测试5: 验证不同的 pageSize
echo -e "${YELLOW}测试5: 验证不同的 pageSize（pageSize=5）${NC}"
RESPONSE5=$(curl -s "${API_BASE}?hours=24&pageNo=1&pageSize=5")
PAGE_SIZE5=$(echo "$RESPONSE5" | jq -r '.pageSize' 2>/dev/null)
DATA_COUNT5=$(echo "$RESPONSE5" | jq '.data | length' 2>/dev/null)

if [ "$PAGE_SIZE5" = "5" ] && [ "$DATA_COUNT5" -le 5 ]; then
    echo -e "${GREEN}✅ 通过: pageSize 参数生效${NC}"
else
    echo -e "${RED}❌ 失败: pageSize 参数未生效${NC}"
fi
echo ""

echo "=========================================="
echo "测试完成"
echo "=========================================="





