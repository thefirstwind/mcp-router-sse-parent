#!/bin/bash
# 等待zkInfo启动并验证参数名修复

echo "⏳ 等待 zkInfo 完全启动..."
sleep 30

echo ""
echo "🔍 检查 zkInfo 是否运行..."
if lsof -ti:9091 >/dev/null 2>&1; then
    echo "✅ zkInfo 正在运行"
else
    echo "❌ zkInfo 未运行！"
    exit 1
fi

echo ""
echo "=========================================="
echo "📋 现在你可以："
echo ""
echo "1. 在zkInfo管理界面提交新的虚拟项目"
echo "   依赖配置："
echo "   <dependency>"
echo "       <groupId>com.zkinfo</groupId>"
echo "       <artifactId>demo-provider3</artifactId>"
echo "       <version>1.0.4</version>"
echo "   </dependency>"
echo ""
echo "2. 等待JAR扫描完成"
echo ""
echo "3. 检查日志中的参数名："
tail -100 /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo/nohup.out | \
    grep -E "(使用真实参数名|参数名)" | tail -5 || echo "   （暂无扫描日志）"

echo ""
echo "4. 创建虚拟节点后，检查配置文件："
echo "   cat virtual-projects/s<N>.json | jq '.tools[] | select(.methodName == \"getUserById\") | .inputSchema'"
echo ""
echo "=========================================="
echo "✨ 预期结果：参数名应该是 'userId' 而不是 'arg0'"
