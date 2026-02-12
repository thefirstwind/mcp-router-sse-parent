
## 6. Git 提交规范 (Commit Convention)

### 6.1 基本格式 (Conventional Commits)
每次提交必须严格遵循以下格式：

```text
<type>(<scope>): <subject>

<detailed description>

### Changes
- <change item 1>
- <change item 2>

### Related Files
- <file/module 1>
- <file/module 2>

## Traceability
Ref: <related_docs_path> (e.g., REQ-ID, Issue-ID)
Trace-ID: <trace_id> (Optional)
```

**Scope 必须明确**: 
- `router`: mcp-router-v3
- `server`: mcp-server-v6
- `zk`: zk-mcp-parent (zkInfo)
- `demo`: demo-provider
- `docs`: 文档变更

**Type**:
- `feat`: 新功能
- `fix`: 修复 bug
- `refactor`: 重构（不改变功能）
- `docs`: 仅文档变更
- `style`: 格式调整
- `test`: 测试相关

### 6.2 详细说明 (Detailed Description)
- **强制要求**: 所有 Commit Message 必须使用 **中文** 编写。
- 清晰描述修改的动机 (Why) 和实现方式 (How)。
- 如果是重构，需说明兼容性影响。

### 6.3 关联性 (Traceability)
- **Ref**: 必须关联相关的需求文档、Issue 或设计文档路径。
- **Trace-ID**: 如果有跨系统的追踪 ID，可以在此记录。
