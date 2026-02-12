#!/bin/bash
# 诊断 getUserById 调用问题
# 等待服务启动30秒后执行测试

echo "等待 zkInfo 服务启动..."
sleep 30

echo "开始测试 getUserById..."

# 测试调用
curl -X POST http://localhost:9091/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.pajk.provider3.service.UserService.getUserById",
    "arguments": {
      "userId": 1
    }
  }' |  jq '.'

echo ""
echo "查看最后50行日志，寻找 Long 转换相关信息..."
tail -50 <(docker logs zkInfo 2>&1 || cat nohup.out 2>/dev/null) | grep -E "(Long|getUserById|转换|参数)"
