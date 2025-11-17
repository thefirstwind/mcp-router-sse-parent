# Response Body 压缩存储方案

## 概述

`routing_logs` 表中的 `response_body` 字段可能超过 2048 字节，为了节省存储空间和提高性能，实现了自动压缩存储方案。

## 实现方案

### 1. 压缩工具类 (`CompressionUtils`)

位置：`com.pajk.mcpbridge.persistence.util.CompressionUtils`

**功能：**
- 自动压缩超过 2048 字节的字符串
- 使用 GZIP 压缩算法
- Base64 编码压缩后的数据
- 添加压缩标记前缀 `[COMPRESSED]` 用于识别
- 自动解压缩带压缩标记的数据

**压缩阈值：** 2048 字节

**压缩格式：** `[COMPRESSED]<Base64编码的GZIP压缩数据>`

**特性：**
- 如果数据已经压缩（有 `[COMPRESSED]` 前缀），不会再次压缩（避免双重压缩）
- 如果数据小于阈值，不压缩，直接返回原数据
- 压缩失败时，返回原数据，不会抛出异常

### 2. MyBatis TypeHandler (`CompressedStringTypeHandler`)

位置：`com.pajk.mcpbridge.persistence.typehandler.CompressedStringTypeHandler`

**功能：**
- 在存储到数据库时，自动压缩 `response_body` 字段
- 在从数据库读取时，自动解压缩 `response_body` 字段
- 对应用层透明，无需手动处理压缩/解压缩

**使用方式：**
在 MyBatis Mapper XML 中指定 TypeHandler：

```xml
<result column="response_body" property="responseBody" 
        typeHandler="com.pajk.mcpbridge.persistence.typehandler.CompressedStringTypeHandler"/>
```

### 3. 业务层处理 (`McpRouterService`)

位置：`com.pajk.mcpbridge.core.service.McpRouterService`

**功能：**
- 在设置 `responseBody` 时，手动调用 `CompressionUtils.compress()` 进行压缩
- 限制响应体最大大小为 50KB（51200 字节）
- 处理 JSON 序列化异常

**注意：**
- 由于 `CompressedStringTypeHandler` 也会自动压缩，`McpRouterService` 中的手动压缩是冗余的
- 但 `CompressionUtils.compress()` 已经实现了防双重压缩检查，所以不会导致问题
- 未来可以考虑移除 `McpRouterService` 中的手动压缩，统一由 TypeHandler 处理

## 压缩流程

### 存储流程

1. 业务层设置 `responseBody`（可能手动压缩）
2. MyBatis TypeHandler 在存储时检查并压缩（如果未压缩）
3. 压缩后的数据存储到数据库

### 读取流程

1. 从数据库读取 `response_body` 字段
2. MyBatis TypeHandler 检查是否有压缩标记
3. 如果有压缩标记，自动解压缩
4. 返回解压缩后的原始数据给应用层

## 压缩效果

- **压缩算法：** GZIP
- **压缩阈值：** 2048 字节
- **压缩标记：** `[COMPRESSED]`
- **压缩比率：** 通常可达到 50-80% 的压缩率（取决于数据内容）

## 使用示例

### 存储响应体

```java
RoutingLog routingLog = new RoutingLog();
String responseBody = "{...}"; // 可能超过 2048 字节
routingLog.setResponseBody(responseBody); // TypeHandler 会自动压缩
routingLogMapper.insert(routingLog);
```

### 读取响应体

```java
RoutingLog routingLog = routingLogMapper.selectById(id);
String responseBody = routingLog.getResponseBody(); // TypeHandler 会自动解压缩
// responseBody 是原始未压缩的数据
```

## 注意事项

1. **双重压缩防护：** `CompressionUtils.compress()` 会检查数据是否已压缩，避免双重压缩
2. **压缩失败处理：** 如果压缩失败，返回原数据，不会抛出异常
3. **解压缩失败处理：** 如果解压缩失败，返回原数据，不会抛出异常
4. **性能影响：** 压缩/解压缩操作会带来一定的 CPU 开销，但可以显著减少存储空间和 I/O 开销
5. **兼容性：** 对于已存储的未压缩数据，读取时会自动识别并返回原数据

## 未来优化建议

1. **移除冗余压缩：** 考虑移除 `McpRouterService` 中的手动压缩，统一由 TypeHandler 处理
2. **配置化阈值：** 将压缩阈值配置化，允许根据实际情况调整
3. **压缩算法选择：** 可以考虑支持多种压缩算法（如 LZ4、Snappy 等），根据数据特点选择最优算法
4. **压缩统计：** 添加压缩统计信息，监控压缩效果和性能影响

