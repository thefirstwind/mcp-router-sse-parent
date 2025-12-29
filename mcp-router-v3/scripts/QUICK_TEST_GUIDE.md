# RESTful 分页接口快速验证指南

## 快速验证步骤

### 1. 使用简单测试脚本（推荐）

```bash
cd mcp-router-v3
./scripts/test-restful-api-simple.sh
```

或者指定服务器地址：

```bash
./scripts/test-restful-api-simple.sh http://your-server:8080
```

### 2. 使用完整测试脚本（需要安装jq）

```bash
# 安装jq (macOS)
brew install jq

# 运行测试
./scripts/test-restful-pagination.sh
```

### 3. 手动curl测试

#### 步骤1: 首次查询
```bash
curl "http://localhost:8080/api/restful-requests?hours=24&limit=10" | jq '.'
```

**检查点**:
- ✅ 返回最多10条记录
- ✅ 记录按时间倒序排列（最新的在前）
- ✅ 每条记录有 `id` 和 `startTime` 字段

#### 步骤2: 提取最后一条记录的时间
```bash
# 获取最后一条记录的startTime
LAST_TIME=$(curl -s "http://localhost:8080/api/restful-requests?hours=24&limit=10" | jq -r '.[-1].startTime')
echo "最后一条记录时间: $LAST_TIME"
```

#### 步骤3: 分页查询
```bash
# 使用最后一条记录的时间进行分页查询
curl "http://localhost:8080/api/restful-requests?hours=24&limit=10&endTime=${LAST_TIME}" | jq '.'
```

**检查点**:
- ✅ 返回最多10条记录
- ✅ 所有记录的时间都早于 `LAST_TIME`
- ✅ 没有与第一次查询重复的记录ID
- ✅ 记录按时间倒序排列

#### 步骤4: 验证无重复
```bash
# 获取两次查询的所有ID
IDS1=$(curl -s "http://localhost:8080/api/restful-requests?hours=24&limit=10" | jq -r '.[].id' | sort)
IDS2=$(curl -s "http://localhost:8080/api/restful-requests?hours=24&limit=10&endTime=${LAST_TIME}" | jq -r '.[].id' | sort)

# 检查重复
comm -12 <(echo "$IDS1") <(echo "$IDS2")
# 如果输出为空，说明没有重复 ✅
```

## 后端日志验证

查看应用日志，应该能看到：

```
RESTful请求分页查询: endTime=2025-01-15T10:30:00, startTime=2025-01-14T10:30:00, hours=24
RESTful请求分页查询结果: endTime=2025-01-15T10:30:00, startTime=2025-01-14T10:30:00, 返回记录数=10, limit=10
分页查询第一条记录时间: 2025-01-15T10:25:00, 最后一条记录时间: 2025-01-15T10:20:00
```

## SQL验证

直接在数据库中执行SQL验证逻辑：

```sql
-- 1. 首次查询（模拟）
SET @now = NOW();
SET @start_time = DATE_SUB(@now, INTERVAL 24 HOUR);
SET @end_time = @now;

SELECT id, request_id, start_time, server_name, mcp_method
FROM routing_logs
WHERE path LIKE '/mcp/router%'
  AND start_time >= @start_time
  AND start_time < @end_time
ORDER BY start_time DESC
LIMIT 10;

-- 2. 获取最后一条记录的时间（假设是 '2025-01-15 10:30:00'）
SET @last_time = '2025-01-15 10:30:00';

-- 3. 分页查询（使用 < 排除最后一条记录）
SELECT id, request_id, start_time, server_name, mcp_method
FROM routing_logs
WHERE path LIKE '/mcp/router%'
  AND start_time >= @start_time
  AND start_time < @last_time  -- 注意：使用 < 而不是 <=
ORDER BY start_time DESC
LIMIT 10;

-- 4. 验证没有重复
-- 比较两次查询的ID列表，应该没有重复
```

## 常见问题排查

### 问题1: 返回空结果
- **原因**: 数据库中没有足够的数据，或者时间范围不正确
- **解决**: 检查数据库中的数据量，调整 `hours` 参数

### 问题2: 有重复数据
- **原因**: SQL条件使用了 `<=` 而不是 `<`
- **解决**: 检查 `RoutingLogMapper.xml` 中的SQL条件，确保使用 `<`

### 问题3: 时间格式错误
- **原因**: `endTime` 参数格式不正确
- **解决**: 确保使用 ISO 格式: `2025-01-15T10:30:00`

### 问题4: 分页不连续
- **原因**: 数据在查询期间发生了变化，或者时间精度问题
- **解决**: 检查数据的时间戳精度，确保使用相同的时间范围

## 预期行为

### ✅ 正确的行为
1. 首次查询返回最新的10条记录
2. 分页查询返回更早的10条记录
3. 两次查询没有重复的记录
4. 记录按时间倒序排列
5. 如果数据不足10条，返回实际数量

### ❌ 错误的行为
1. 两次查询有重复的记录 ❌
2. 分页查询返回的数据时间晚于上次查询的最后一条 ❌
3. 返回的记录数超过limit ❌
4. 记录顺序不正确 ❌

## 下一步

如果后端接口验证通过，再检查前端滚动加载功能。










