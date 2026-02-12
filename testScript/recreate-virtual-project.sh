#!/bin/bash
# 重新创建虚拟项目以使用新的JAR包（1.0.4）
# 这会从Maven仓库下载新的JAR并重新扫描

ZKINFO_URL="http://localhost:9091"
SESSION_ID="rescan-$(date +%s)"

echo "🔄 重新创建虚拟项目以更新参数名..."
echo "=========================================="

# 1. 准备POM内容（使用新版本1.0.4）
POM_CONTENT='<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.zkinfo</groupId>
    <artifactId>virtual-demo-provider3</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
        <dependency>
            <groupId>com.zkinfo</groupId>
            <artifactId>demo-provider3</artifactId>
            <version>1.0.4</version>
        </dependency>
    </dependencies>
</project>'

echo ""
echo "1️⃣ 触发POM解析（下载JAR 1.0.4并扫描）..."

# 启动SSE监听（后台）
echo "启动SSE进度监听..."
curl -N "${ZKINFO_URL}/api/wizard/parse-progress?sessionId=${SESSION_ID}" &
SSE_PID=$!

sleep 2

# 发起解析请求
PARSE_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/api/wizard/parse-pom" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectName\": \"demo-provider3-v2\",
    \"pomContent\": $(echo "$POM_CONTENT" | jq -Rs .),
    \"sessionId\": \"${SESSION_ID}\"
  }")

echo "解析响应: $PARSE_RESPONSE"

echo ""
echo "等待扫描完成（查看上方SSE输出）..."
sleep 10

# 停止SSE监听
kill $SSE_PID 2>/dev/null

echo ""
echo "=========================================="
echo "📋 接下来需要在UI中完成:"
echo "1. 打开 ${ZKINFO_URL} 查看解析结果"
echo "2. 确认扫描到的接口包含 'userId' 参数"
echo "3. 提交创建新的虚拟项目"
echo "4. 在MCP Inspector中连接新虚拟节点"
echo "5. 验证 getUserById 参数名为 'userId'"
