#!/bin/bash
# 测试JAR扫描是否能读取参数名

echo "📦 测试 demo-provider3-1.0.4.jar 参数名读取"
echo "=========================================="

JAR_PATH="$HOME/.m2/repository/com/zkinfo/demo-provider3/1.0.4/demo-provider3-1.0.4.jar"

echo ""
echo "1️⃣ 验证JAR文件存在:"
if [ -f "$JAR_PATH" ]; then
    echo "✅ JAR文件存在: $JAR_PATH"
    ls -lh "$JAR_PATH"
else
    echo "❌ JAR文件不存在!"
    exit 1
fi

echo ""
echo "2️⃣ 提取并验证UserService.class的参数名:"
cd /tmp
jar xf "$JAR_PATH" com/pajk/provider3/service/UserService.class 2>/dev/null

if javap -v com/pajk/provider3/service/UserService.class 2>/dev/null | grep -q "userId"; then
    echo "✅ class文件包含参数名 'userId'"
    javap -v com/pajk/provider3/service/UserService.class | grep -A 5 "getUserById"
else
    echo "❌ class文件不包含参数名 'userId'，使用的可能是旧版本"
    javap -v com/pajk/provider3/service/UserService.class | grep -A 5 "getUserById"
fi

echo ""
echo "3️⃣ 检查zkInfo是否使用了新版本:"
echo "请检查以下位置："
echo "  - 虚拟节点是否重新创建？"
echo "  - 数据库中的元数据是否更新？"
echo "  - MCP Inspector显示的参数名是什么？"

echo ""
echo "=========================================="
echo "📋 下一步操作:"
echo "1. 在zkInfo管理界面删除旧的虚拟节点"
echo "2. 重新上传JAR: $JAR_PATH"
echo "3. 重新扫描并创建虚拟节点"
echo "4. 在MCP Inspector中验证参数名为 'userId'"
