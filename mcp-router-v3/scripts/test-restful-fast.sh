#!/bin/bash

echo "=== 测试 RESTful 接口响应时间 ==="
echo ""

DOMAIN="mcp-bridge.local"
IP="127.0.0.1"
ENDPOINT="/mcp/router/tools/mcp-server-v6"

# 方法1：使用 --resolve（推荐）
echo "1. 使用 --resolve（绕过 DNS）："
time curl --resolve ${DOMAIN}:80:${IP} \
  -s -o /dev/null \
  -w "状态码: %{http_code}\n总时间: %{time_total}s\n" \
  http://${DOMAIN}${ENDPOINT}
echo ""

# 方法2：直接使用 IP
echo "2. 直接使用 IP："
time curl -s -o /dev/null \
  -w "状态码: %{http_code}\n总时间: %{time_total}s\n" \
  http://${IP}${ENDPOINT}
echo ""

# 方法3：使用域名（如果 DNS 已修复）
echo "3. 使用域名（如果 DNS 已修复）："
time curl -s -o /dev/null \
  -w "状态码: %{http_code}\n总时间: %{time_total}s\n" \
  http://${DOMAIN}${ENDPOINT}
echo ""

echo "=== 推荐使用方法 1（--resolve）==="
