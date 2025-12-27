# RESTful 请求列表分页接口测试指南

## 新的分页设计

使用标准的 `pageNo` 和 `pageSize` 参数进行分页，而不是基于时间戳的分页。

### API 接口

**接口**: `GET /api/restful-requests`

**参数**:
- `pageNo` (可选, 默认1): 页码，从1开始
- `pageSize` (可选, 默认10): 每页大小，范围1-100
- `hours` (可选, 默认24): 查询最近N小时的数据
- `serviceName` (可选): 服务名称过滤
- `mcpMethod` (可选): MCP方法过滤
- `hasSessionId` (可选): sessionId过滤

**响应格式**:
```json
{
  "pageNo": 1,
  "pageSize": 10,
  "totalCount": 100,
  "totalPages": 10,
  "data": [
    {
      "id": 1,
      "requestId": "...",
      "startTime": "...",
      ...
    }
  ]
}
```

## 快速测试

### 方法1: 使用测试脚本

```bash
cd mcp-router-v3
./scripts/test-restful-pagination-v2.sh
```

### 方法2: 手动curl测试

#### 测试第一页
```bash
curl "http://localhost:8080/api/restful-requests?hours=24&pageNo=1&pageSize=10" | jq '.'
```

#### 测试第二页
```bash
curl "http://localhost:8080/api/restful-requests?hours=24&pageNo=2&pageSize=10" | jq '.'
```

#### 测试最后一页（假设总页数是10）
```bash
curl "http://localhost:8080/api/restful-requests?hours=24&pageNo=10&pageSize=10" | jq '.'
```

## 验证要点

1. ✅ **分页参数正确**: `pageNo` 和 `pageSize` 应该与请求参数一致
2. ✅ **总记录数正确**: `totalCount` 应该反映符合条件的总记录数
3. ✅ **总页数正确**: `totalPages = ceil(totalCount / pageSize)`
4. ✅ **数据量正确**: `data.length <= pageSize`
5. ✅ **无重复数据**: 不同页码之间不应该有重复的记录ID
6. ✅ **数据顺序**: 应该按 `start_time DESC` 排序

## SQL 查询逻辑

```sql
-- 计算总数
SELECT COUNT(*) FROM routing_logs 
WHERE path LIKE '/mcp/router%' 
  AND start_time >= ? AND start_time <= ?;

-- 查询分页数据
SELECT * FROM routing_logs 
WHERE path LIKE '/mcp/router%' 
  AND start_time >= ? AND start_time <= ?
ORDER BY start_time DESC
LIMIT ? OFFSET ?;
```

其中：
- `LIMIT` = `pageSize`
- `OFFSET` = `(pageNo - 1) * pageSize`

## 后端日志

查看应用日志，应该能看到：

```
RESTful请求分页查询: pageNo=1, pageSize=10, totalCount=100, totalPages=10, 返回记录数=10
```

## 前端使用

前端代码已经更新为使用新的分页接口：
- 使用 `pageNo` 和 `pageSize` 参数
- 处理新的响应格式（包含 `totalCount`, `totalPages` 等）
- 滚动加载时自动递增 `pageNo`









