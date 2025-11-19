# 路由日志字段压缩方案（Brotli + GZIP 兼容）

## 背景

`routing_logs` 表中的请求/响应体在高并发场景下可能远超 TEXT 列限制。为保证 **无损存储**，我们引入 Brotli 压缩作为首选方案，并对历史 `[COMPRESSED]`（GZIP）数据保持兼容。

## 架构概览

1. **统一压缩工具**  
   - `com.pajk.mcpbridge.persistence.util.CompressionUtils`
   - 优先使用 Brotli（`brotli4j`），不可用时自动回退 GZIP
   - 添加前缀：
     - `[BROTLI]` + Base64(Brotli bytes)
     - `[COMPRESSED]` + Base64(GZIP bytes) —— 兼容旧数据

2. **MyBatis TypeHandler**  
   - `ThresholdCompressedStringTypeHandler` 负责按字段阈值决定是否压缩
   - 四个子类对应不同阈值（单位：字节）  
     | 字段 | TypeHandler | 阈值 |
     | --- | --- | --- |
     | `request_headers` | `RequestHeadersTypeHandler` | 512 |
     | `request_body` | `RequestBodyTypeHandler` | 1024 |
     | `response_headers` | `ResponseHeadersTypeHandler` | 512 |
     | `response_body` | `ResponseBodyTypeHandler` | 1024 |
   - 读取时自动解压，应用层拿到始终是原始文本

3. **Mapper 配置**  
   ```xml
   <result column="response_body"
           property="responseBody"
           typeHandler="com.pajk.mcpbridge.persistence.typehandler.ResponseBodyTypeHandler"/>
   ```
   `insert` / `batchInsert` 语句同样使用 `#{log.responseBody, typeHandler=...}`，确保批量写入也走压缩流程。

4. **业务层策略**  
   - `McpRouterService` 只负责软限制（50 KB）和序列化
   - 真正的压缩/解压完全由 TypeHandler 透明处理

## 压缩流程

1. TypeHandler 判断字节长度是否超过阈值
2. 若超过：
   - 尝试 Brotli（质量 6，TEXT 模式）
   - Brotli 失败则回退 GZIP
3. 若压缩后仍超限，则 fallback 为原文截断（追加 `...[TRUNCATED]`），保证写入成功
4. 最终写入的数据：
   - 成功压缩 → `[BROTLI]base64` 或 `[COMPRESSED]base64`
   - 压缩失败 → 原文（并写告警）
   - 压缩后仍超限 → 截断标记文本
5. 读取时：
   - `[BROTLI]` → Brotli 解码
   - `[COMPRESSED]` → GZIP 解码
   - 无前缀 → 原文

日志示例：
```
TypeHandler ResponseBodyTypeHandler: Brotli compressed from 3060 bytes to 620 bytes (threshold=1024)
TypeHandler ResponseBodyTypeHandler: compressed payload still exceeds limit (1120>1024), applying truncation fallback.
```

## 兼容性

- 历史数据（`[COMPRESSED]`）仍可正常读取
- 若未来需要再引入新算法，可扩展 CompressionUtils 前缀即可

## FAQ

**Q: 如果压缩后仍超出列限制怎么办？**  
TypeHandler 会回退到“截断原文 + `...[TRUNCATED]`”的策略，确保写入数据库成功，同时在日志中输出 WARN。

**Q: 是否可以自定义阈值？**  
可以在各 TypeHandler 子类中调整常量，或引入配置化参数。

**Q: 会不会影响性能？**  
Brotli 在质量 6 + TEXT 模式下性能与 GZIP 相当，结合阈值判断只在大 payload 执行压缩，对整体开销可控。

## 参考

- `CompressionUtils` —— Brotli/GZIP 编解码实现
- `ThresholdCompressedStringTypeHandler` —— 压缩阈值控制
- `RoutingLogBatchWriter` —— 捕获数据库 “Data too long” 时的排查日志

