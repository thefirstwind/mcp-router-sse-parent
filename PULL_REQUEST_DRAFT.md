## Description
修复 Streamable 协议的 session 会话管理问题，确保客户端能够可靠地获取和使用 sessionId。

## Type of Change
- [x] 🐛 Bug fix (修复 bug)
- [ ] ✨ New feature (新功能)
- [ ] 🔧 Configuration change (配置变更)
- [ ] 📝 Documentation update (文档更新)
- [ ] ♻️ Refactoring (重构)
- [ ] 🎨 Style update (代码风格)
- [ ] ⚡ Performance improvement (性能优化)
- [x] ✅ Test update (测试更新)

## Related Issues
修复 Streamable 协议的 session 管理问题，某些客户端（如 MCP Inspector）在 Streamable 模式下未正确处理 sessionId。

## Changes Made

### 1. 增强 Streamable 初始连接
- **文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`
- **修改**: 在 NDJSON 流的开头添加 session 信息消息
- **新增方法**: `buildSessionIdMessage(String sessionId, String messageEndpoint)`
- **效果**: 客户端可以从第一条 NDJSON 消息中获取 sessionId，解决了响应头处理不当的问题

### 2. 增强 Session ID 解析日志
- **文件**: 同上
- **修改**: 改进 `resolveSessionId(ServerRequest request)` 方法
- **新增功能**: 
  - 详细记录 sessionId 的解析来源（请求头或查询参数）
  - 当未找到 sessionId 时记录警告并提供清晰的错误提示
- **效果**: 更容易诊断 session 管理问题

### 3. 添加测试脚本
- **文件**: `test_streamable_session.sh`
- **内容**: 
  - 测试 GET /mcp 的 session 初始化
  - 测试 POST /mcp/message 的 sessionId 解析
  - 验证响应头中的 `Mcp-Session-Id`
  - 提供详细的日志分析指南
- **效果**: 提供自动化测试验证修复效果

### 4. 添加文档
- **文件**: `STREAMABLE_SESSION_FIX.md` - 问题分析和修复方案
- **文件**: `BUGFIX_SUMMARY.md` - 修复总结和测试指南

## Testing

- [x] 单元测试通过（现有测试）
- [ ] 集成测试通过（需要运行）
- [x] 手动测试完成（通过测试脚本）

测试步骤:
1. 启动 mcp-router-v3: `mvn spring-boot:run`
2. 运行测试脚本: `./test_streamable_session.sh`
3. 检查日志确认 sessionId 解析正确
4. 验证第一条 NDJSON 消息包含 session 信息

**验证命令**:
```bash
# 测试 Streamable 连接
curl -N -H "Accept: application/x-ndjson" \
  "http://localhost:18791/mcp/mcp-server-v6" | head -n 1

# 预期输出
{"type":"session","sessionId":"xxx-xxx-xxx","messageEndpoint":"...","transport":"streamable"}

# 测试 sessionId 通过请求头传递
curl -X POST -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: test-123" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list"}' \
  "http://localhost:18791/mcp/mcp-server-v6/message"
```

## Checklist

- [x] 代码遵循项目规范
- [x] 已添加必要的注释和文档
- [x] 已更新相关文档（STREAMABLE_SESSION_FIX.md, BUGFIX_SUMMARY.md）
- [x] 所有测试通过（现有测试）
- [x] 无新增 lint 警告
- [x] 已进行自我代码审查

## Screenshots (if applicable)

N/A - 这是后端 API 修复，没有 UI 变更

## Additional Notes

### 修复背景
根据代码注释（`McpRouterService.java:698`）：
> "为了兼容当前 MCP Inspector 等客户端在 Streamable 模式下未传 sessionId 的情况"

这个修复解决了某些客户端未正确处理 `Mcp-Session-Id` 响应头的问题。

### 解决方案
1. **多重传递机制**: 通过响应头 + NDJSON 初始消息双重传递 sessionId
2. **增强日志**: 详细记录 sessionId 解析过程，方便问题诊断
3. **向后兼容**: 保持对查询参数方式的支持

### 影响范围
- **核心模块**: mcp-router-v3
- **影响组件**: Streamable 协议处理
- **向后兼容**: ✅ 是
- **破坏性变更**: ❌ 否

### 后续建议
1. 鼓励客户端开发者使用 `Mcp-Session-Id` 请求头（官方规范）
2. 监控日志中的 sessionId 解析警告
3. 考虑在未来版本中添加 session 管理的 metrics

### 相关文档
- [Streamable 问题分析](./STREAMABLE_SESSION_FIX.md)
- [修复总结](./BUGFIX_SUMMARY.md)
- [测试脚本](./test_streamable_session.sh)

---

**提交**:
- `08ecd83` fix(streamable): enhance session management for streamable protocol
- `8f58530` test(streamable): add session management verification script
