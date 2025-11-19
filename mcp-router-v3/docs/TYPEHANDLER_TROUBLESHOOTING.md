# TypeHandler 问题排查指南

## 问题描述

在生产环境中，`response_body` 等字段更新时超过限制报错，通常是 Brotli 压缩 TypeHandler 没有生效。

## 可能的原因

1. **TypeHandler 包路径未配置** —— `MyBatisConfig` 必须调用 `setTypeHandlersPackage(...)`。
2. **Mapper XML 未显式指定 TypeHandler** —— 包括 `insert`、`batchInsert`。
3. **部署包缺少最新类/依赖** —— 需要确保 `brotli4j` 与自定义 TypeHandler 已随 WAR/JAR 发布。

## 排查步骤

### 1. 检查启动日志

启动时应该看到以下日志：

```
TypeHandler RequestHeadersTypeHandler initialized. Byte limit: 512
TypeHandler RequestBodyTypeHandler initialized. Byte limit: 1024
TypeHandler ResponseHeadersTypeHandler initialized. Byte limit: 512
TypeHandler ResponseBodyTypeHandler initialized. Byte limit: 1024
MyBatis Configuration initialized. TypeHandlers registered: X
```

如果没有看到这些日志，说明 TypeHandler 没有被加载。

### 2. 检查 MyBatis 配置

确认 `MyBatisConfig.java` 中有以下配置：

```java
// 设置 TypeHandler 包路径（重要：确保 TypeHandler 被正确加载）
sessionFactory.setTypeHandlersPackage("com.pajk.mcpbridge.persistence.typehandler");
```

### 3. 检查 Mapper XML

确认 `RoutingLogMapper.xml` 中所有相关字段都指定了 TypeHandler：

```xml
#{log.requestHeaders, typeHandler=com.pajk.mcpbridge.persistence.typehandler.RequestHeadersTypeHandler}
#{log.requestBody, typeHandler=com.pajk.mcpbridge.persistence.typehandler.RequestBodyTypeHandler}
#{log.responseHeaders, typeHandler=com.pajk.mcpbridge.persistence.typehandler.ResponseHeadersTypeHandler}
#{log.responseBody, typeHandler=com.pajk.mcpbridge.persistence.typehandler.ResponseBodyTypeHandler}
```

### 4. 检查错误日志

当出现数据过长错误时，`RoutingLogBatchWriter` 会输出哪几个字段溢出，以及可能的排查建议，请确认是否提示 “This may indicate TypeHandler not working”。

### 5. 验证 TypeHandler 是否工作

在日志中查找以下信息：

```
TypeHandler ResponseBodyTypeHandler: Brotli compressed from 3060 bytes to 640 bytes (threshold=1024)
TypeHandler ResponseBodyTypeHandler: compressed payload still exceeds limit (1120>1024), applying truncation fallback.
```

如果看到类似日志，说明压缩链路正常。

## 压缩阈值

- `request_headers`: 512 字节
- `request_body`: 1024 字节
- `response_headers`: 512 字节
- `response_body`: 1024 字节

超过阈值的数据会自动压缩（首选 Brotli，失败回退 GZIP）；若压缩后仍超限，则自动截断并附加 `...[TRUNCATED]`。

## 解决方案

1. **确保 MyBatisConfig 配置正确** —— 启动日志需显示 TypeHandler 数量。
2. **确保 Mapper XML 配置正确** —— 四个字段均需显式 typeHandler。
3. **检查生产环境部署** —— 确保 `brotli4j` 依赖及相关类已经打包。
4. **结合日志分析** —— 若仍有 “Data too long”，查看日志中样本大小是否已经压缩，必要时放宽数据库列或外部存储。

## 日志级别

建议在生产环境中设置以下日志级别：

```yaml
logging:
  level:
    com.pajk.mcpbridge.persistence.typehandler: DEBUG
    com.pajk.mcpbridge.persistence.config: INFO
    com.pajk.mcpbridge.persistence.service: INFO
```

这样可以更好地追踪 TypeHandler 的工作情况。

