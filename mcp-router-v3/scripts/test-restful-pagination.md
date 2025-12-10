# RESTful 请求列表分页接口测试文档

## 接口说明

**接口地址**: `GET /api/restful-requests`

**参数说明**:
- `hours` (可选, 默认1): 查询最近N小时的数据
- `limit` (可选, 默认100): 限制返回数量
- `endTime` (可选): 结束时间，用于分页（ISO格式: `2025-01-15T10:30:00`）
- `serviceName` (可选): 服务名称过滤
- `mcpMethod` (可选): MCP方法过滤
- `hasSessionId` (可选): sessionId过滤

## 分页逻辑

1. **首次查询**: 不传 `endTime`，查询最近N小时的数据，按时间倒序返回前N条
2. **分页查询**: 传入上次查询最后一条记录的 `startTime` 作为 `endTime`，查询更早的数据
3. **SQL条件**: `start_time < #{endTime} AND start_time >= #{startTime}`，使用 `<` 排除最后一条记录

## 手动测试步骤

### 1. 首次查询（获取前10条）

```bash
curl "http://localhost:8080/api/restful-requests?hours=24&limit=10"
```

**预期结果**:
- 返回最多10条记录
- 记录按 `start_time` 倒序排列（最新的在前）
- 每条记录包含 `id`, `startTime` 等字段

### 2. 分页查询（使用最后一条记录的时间）

假设第一次查询返回的最后一条记录的 `startTime` 是 `2025-01-15T10:30:00`:

```bash
curl "http://localhost:8080/api/restful-requests?hours=24&limit=10&endTime=2025-01-15T10:30:00"
```

**预期结果**:
- 返回最多10条记录
- 所有记录的时间都早于 `2025-01-15T10:30:00`
- 没有与第一次查询重复的记录
- 记录按时间倒序排列

### 3. 继续分页

使用第二次查询的最后一条记录的 `startTime` 继续查询:

```bash
curl "http://localhost:8080/api/restful-requests?hours=24&limit=10&endTime=2025-01-15T09:20:00"
```

## 验证要点

### ✅ 正确性验证

1. **无重复数据**: 每次查询返回的记录ID不应该与之前的查询重复
2. **时间顺序**: 分页查询返回的数据时间应该早于上次查询的最后一条记录
3. **数据连续性**: 如果数据充足，每次应该返回10条记录
4. **时间范围**: 所有记录的时间应该在 `startTime` 和 `endTime` 之间

### ❌ 常见问题

1. **重复数据**: SQL条件应该使用 `<` 而不是 `<=`，排除最后一条记录
2. **时间格式**: `endTime` 参数需要是 ISO 格式的 LocalDateTime 字符串
3. **空结果**: 如果返回空数组，可能是没有更多数据，或者时间范围不正确

## 使用测试脚本

运行自动化测试脚本:

```bash
# 使用默认地址 (http://localhost:8080)
./scripts/test-restful-pagination.sh

# 指定服务器地址
./scripts/test-restful-pagination.sh http://your-server:8080
```

## SQL 查询验证

可以在数据库中直接执行SQL验证:

```sql
-- 首次查询（模拟）
SELECT id, request_id, start_time, server_name, mcp_method
FROM routing_logs
WHERE path LIKE '/mcp/router%'
  AND start_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND start_time < NOW()
ORDER BY start_time DESC
LIMIT 10;

-- 分页查询（假设最后一条记录的start_time是 '2025-01-15 10:30:00'）
SELECT id, request_id, start_time, server_name, mcp_method
FROM routing_logs
WHERE path LIKE '/mcp/router%'
  AND start_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND start_time < '2025-01-15 10:30:00'  -- 注意使用 < 而不是 <=
ORDER BY start_time DESC
LIMIT 10;
```

## 调试建议

1. **查看后端日志**: 检查 `log.debug` 输出的分页查询信息
2. **检查时间格式**: 确保前端传递的 `endTime` 格式正确
3. **验证SQL**: 直接在数据库中执行SQL，确认查询逻辑正确
4. **检查数据量**: 确保数据库中有足够的数据进行分页测试


