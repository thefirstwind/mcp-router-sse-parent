## 📖 描述
修复 Streamable 协议的 session 会话管理问题，确保客户端能够可靠地获取和使用 sessionId。

## 🔧 变更类型
- [x] 🐛 Bug fix (修复 bug)
- [x] ✅ Test update (测试更新)
- [x] 📝 Documentation update (文档更新)

## 🎯 相关问题
修复 Streamable 协议的 session 管理问题，某些客户端（如 MCP Inspector）在 Streamable 模式下未正确处理 sessionId。

## ✨ 主要变更

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

### 3. 完整的测试体系
- **快速测试**: `test_streamable_session.sh`
- **全面测试**: `test_streamable_comprehensive.sh` (20+ 测试用例)
- **CI/CD**: `.github/workflows/test-streamable-session.yml` 自动化测试
- **覆盖范围**: NDJSON 格式、响应头、会话生命周期、并发连接等

### 4. 完整的可追溯性系统
- **特性文档**: `docs/features/streamable-session-management.md`
- **架构决策**: `docs/adr/001-streamable-session-dual-transmission.md`
- **需求追溯**: `docs/traceability/streamable-session.md`
- **最佳实践**: `docs/reference/best-practices-traceability.md`

## 🧪 测试验证

验证命令:
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

运行测试脚本:
```bash
./test_streamable_session.sh           # 快速测试
./test_streamable_comprehensive.sh     # 全面测试
```

## ✅ 检查清单

- [x] 代码遵循项目规范
- [x] 已添加必要的注释和文档
- [x] 已更新相关文档
- [x] 所有测试通过
- [x] 无新增 lint 警告
- [x] 已进行自我代码审查
- [x] CI/CD 自动化测试已配置

## 📊 影响范围
- **核心模块**: mcp-router-v3
- **影响组件**: Streamable 协议处理
- **向后兼容**: ✅ 是
- **破坏性变更**: ❌ 否

## 🔍 解决方案
1. **双重传递机制**: 通过响应头 + NDJSON 初始消息双重传递 sessionId
2. **增强日志**: 详细记录 sessionId 解析过程，方便问题诊断
3. **向后兼容**: 保持对查询参数方式的支持

## 📚 相关文档
- [Streamable Session Management 特性文档](./docs/features/streamable-session-management.md)
- [ADR-001: Streamable Session 双重传递机制](./docs/adr/001-streamable-session-dual-transmission.md)
- [需求追溯矩阵](./docs/traceability/streamable-session.md)
- [可追溯性最佳实践](./docs/reference/best-practices-traceability.md)
- [修复总结](./BUGFIX_SUMMARY.md)

## 💡 后续建议
1. 鼓励客户端开发者使用 `Mcp-Session-Id` 请求头（官方规范）
2. 监控日志中的 sessionId 解析警告
3. 考虑在未来版本中添加 session 管理的 metrics
