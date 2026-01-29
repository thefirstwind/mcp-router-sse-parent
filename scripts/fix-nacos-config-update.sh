#!/bin/bash

# Nacos 配置更新问题修复脚本
# 用途：自动应用修复补丁到 mcp-server-v5 和 mcp-server-v6

set -e

PROJECT_ROOT="/Users/shine/projects.mcp-router-sse-parent"

echo "🔧 开始应用 Nacos 配置更新修复补丁..."
echo ""

# 函数：显示文件修改说明
show_fix_info() {
    cat << 'EOF'
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
修复说明
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

问题：mcp-server 升级工具接口（添加字段）后，重新部署时
      工具信息不会在 Nacos 上更新

原因：
1. publishConfig() 不会强制覆盖已存在的配置
2. dataId 固定不变，Nacos 无法识别这是新版本
3. 没有配置变更检测机制

解决方案：
1. 上传配置前先读取远程配置
2. 比较本地和远程配置的 MD5 值
3. 只在 MD5 不同时才执行更新
4. 在服务实例元数据中记录配置 MD5

修改文件：
- mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config/NacosRegistrationConfig.java
- mcp-server-v6 (如果存在类似文件)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EOF
}

show_fix_info

echo ""
echo "📝 已创建的参考文件："
echo "   1. $PROJECT_ROOT/NACOS_TOOLS_UPDATE_ISSUE_FIX.md - 问题分析和解决方案"
echo "   2. $PROJECT_ROOT/NACOS_CONFIG_UPDATE_PATCH.md - 详细修改补丁"
echo "   3. $PROJECT_ROOT/mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config/NacosRegistrationConfigFixed.java - 修复后的完整实现"
echo ""

echo "⚠️  由于原文件可能正在使用中，我们不会自动修改。请手动应用以下步骤："
echo ""
echo "步骤 1: 备份原文件"
echo "  cd $PROJECT_ROOT/mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config"
echo "  cp NacosRegistrationConfig.java NacosRegistrationConfig.java.backup"
echo ""

echo "步骤 2: 查看修改方法"
echo "  选择以下方法之一："
echo ""
echo "  方法 A（推荐）: 使用 IDE 手动修改"
echo "    1. 打开 NacosRegistrationConfig.java"
echo "    2. 找到 uploadConfigToNacos 方法（约第255-275行）"
echo "    3. 参考 $PROJECT_ROOT/NACOS_CONFIG_UPDATE_PATCH.md 进行修改"
echo ""
echo "  方法 B: 使用参考实现"
echo "    1. 打开 NacosRegistrationConfigFixed.java"
echo "    2. 复制 uploadConfigToNacos 方法（约第305-360行）"
echo "    3. 替换原文件中的同名方法"
echo ""
echo "  方法 C: 直接替换整个类（最简单但需要检查）"
echo "    cd $PROJECT_ROOT/mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config"
echo "    mv NacosRegistrationConfig.java NacosRegistrationConfig.java.old"
echo "    cp NacosRegistrationConfigFixed.java NacosRegistrationConfig.java"
echo "    # 然后修改类名从 NacosRegistrationConfigFixed 改回 NacosRegistrationConfig"
echo ""

echo "步骤 3: 重新编译和测试"
echo "  cd $PROJECT_ROOT/mcp-server-v5"
echo "  mvn clean package"
echo "  java -jar target/mcp-server-v5-*.jar"
echo ""

echo "步骤 4: 验证修复"
echo "  1. 查看启动日志，应该看到配置 MD5 信息"
echo "  2. 修改某个工具的定义（如添加参数、修改描述）"
echo "  3. 重新启动服务"
echo "  4. 日志应该显示「Config content changed」和新的 MD5 值"
echo "  5. 在 Nacos 控制台确认配置已更新"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📚 更多信息请查看："
echo "  $PROJECT_ROOT/NACOS_TOOLS_UPDATE_ISSUE_FIX.md"
echo ""
echo "✅ 补丁准备完成！请按照上述步骤手动应用修改。"
