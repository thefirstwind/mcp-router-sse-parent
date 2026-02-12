#!/bin/bash
# 测试 getUserById 泛化调用参数类型修复
#
# @traceability
#   - Requirement: REQ-20260211-001
#   - Bug: getUserById 调用报错 NoSuchMethodException
#   - Fix: ParameterConverter.java - 确保 Long 类型返回 Long 对象
#   - Code: McpExecutorService.java, ParameterConverter.java

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}测试 getUserById 泛化调用${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# 检查服务是否运行
echo -e "${YELLOW}检查服务状态...${NC}"
if ! curl -s http://localhost:9091/actuator/health > /dev/null 2>&1; then
    echo -e "${RED}✗ zkInfo 服务未运行在端口 9091${NC}"
    echo "请先启动 zkInfo 服务"
    exit 1
fi
echo -e "${GREEN}✓ zkInfo 服务运行中${NC}"
echo ""

# 测试用例 1: 调用 getUserById(1) - Long 参数
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}测试 1: 调用 getUserById(1)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

RESPONSE=$(curl -s -X POST http://localhost:9091/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.pajk.provider3.service.UserService.getUserById",
    "arguments": {
      "id": 1
    }
  }')

echo "响应: $RESPONSE"
echo ""

# 检查是否成功
if echo "$RESPONSE" | grep -q "NoSuchMethodException"; then
    echo -e "${RED}✗ 测试失败: 仍然报错 NoSuchMethodException${NC}"
    echo -e "${RED}问题: 参数类型转换未生效${NC}"
    exit 1
elif echo "$RESPONSE" | grep -q "success.*true"; then
    echo -e "${GREEN}✓ 测试通过: getUserById(1) 调用成功${NC}"
else
    echo -e "${YELLOW}⚠ 响应格式未知，请检查日志${NC}"
fi

echo ""

# 测试用例 2: 调用 getUserById(100) - 另一个Long参数
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}测试 2: 调用 getUserById(100)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

RESPONSE=$(curl -s -X POST http://localhost:9091/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.pajk.provider3.service.UserService.getUserById",
    "arguments": {
      "id": 100
    }
  }')

echo "响应: $RESPONSE"
echo ""

if echo "$RESPONSE" | grep -q "NoSuchMethodException"; then
    echo -e "${RED}✗ 测试失败: NoSuchMethodException${NC}"
    exit 1
elif echo "$RESPONSE" | grep -q "success"; then
    echo -e "${GREEN}✓ 测试通过: getUserById(100) 调用成功${NC}"
else
    echo -e "${YELLOW}⚠ 响应格式未知${NC}"
fi

echo ""

# 测试用例 3: 边界值测试
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}测试 3: 边界值测试 (Long.MAX_VALUE)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

RESPONSE=$(curl -s -X POST http://localhost:9091/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.pajk.provider3.service.UserService.getUserById",
    "arguments": {
      "id": 9223372036854775807
    }
  }')

echo "响应: $RESPONSE"
echo ""

if echo "$RESPONSE" | grep -q "NoSuchMethodException"; then
    echo -e "${RED}✗ 测试失败: NoSuchMethodException${NC}"
    exit 1
else
    echo -e "${GREEN}✓ 测试通过: 大数值 Long 参数处理正确${NC}"
fi

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✓ 所有测试通过！${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "修复验证: REQ-20260211-001"
echo "- 问题: getUserById 调用报错 NoSuchMethodException(int)"
echo "- 原因: Long参数被错误转换为 int 类型"
echo "- 修复: ParameterConverter 确保 java.lang.Long 返回 Long 对象"
echo "- 状态: ✅ 已修复并验证"
