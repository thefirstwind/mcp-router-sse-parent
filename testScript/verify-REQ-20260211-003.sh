#!/bin/bash
# @traceability
#   - Requirement: REQ-20260211-003
#   - Code: McpProtocolService.java
#   - Configuration: s14.json

echo "验证 REQ-20260211-003: 虚拟项目参数类型修复"

# 检查 s14.json 是否包含 parameterTypes
if grep -q "parameterTypes" zk-mcp-parent/zkInfo/virtual-projects/s14.json; then
    echo "✓ s14.json 已包含 parameterTypes"
else
    echo "✗ s14.json 缺失 parameterTypes"
    exit 1
fi

# 检查 McpProtocolService.java 是否包含 @traceability
if grep -q "@traceability.*REQ-20260211-003" zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/McpProtocolService.java; then
    echo "✓ McpProtocolService.java 已包含追溯标记"
else
    echo "✗ McpProtocolService.java 缺失追溯标记"
    exit 1
fi

echo "验证通过！"
