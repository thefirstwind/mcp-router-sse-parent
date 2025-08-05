# MCP Router V3 Logback 配置说明

## 概述

MCP Router V3 现已完全支持 Logback 日志框架，提供了丰富的日志配置功能，包括：

- 控制台输出
- 文件滚动日志
- 错误日志分离
- 环境特定配置
- 性能优化的日志级别控制

## 配置文件

### 主配置文件
- `src/main/resources/logback-spring.xml` - Logback 主配置文件
- `src/main/resources/application.yml` - Spring Boot 日志配置引用

## 日志输出配置

### 1. 控制台输出 (CONSOLE)
- 格式：`%d{yyyy-MM-dd HH:mm:ss.SSS} %5p --- [%15.15t] %-40.40logger{39} : %m%n`
- 编码：UTF-8
- 输出所有级别的日志

### 2. 文件输出 (FILE)
- 文件位置：`logs/mcp-router-v3.log`
- 滚动策略：按时间（每日）+ 按大小（50MB）
- 保留策略：30天，总大小限制5GB
- 压缩：自动 gzip 压缩历史文件

### 3. 错误日志 (ERROR_FILE)
- 文件位置：`logs/mcp-router-v3-error.log`
- 只记录 ERROR 级别日志
- 包含完整的异常堆栈信息
- 滚动策略：按时间（每日）+ 按大小（10MB）
- 保留策略：30天，总大小限制1GB

## 环境特定配置

### 开发环境 (dev/development)
```xml
<springProfile name="dev,development">
  <!-- DEBUG 级别的详细日志 -->
  <logger name="com.nacos.mcp.router.v3" level="DEBUG"/>
  <logger name="org.springframework.ai" level="DEBUG"/>
  <logger name="com.alibaba.cloud.ai" level="DEBUG"/>
  <logger name="com.alibaba.nacos" level="DEBUG"/>
</springProfile>
```

### 生产环境 (prod/production)
```xml
<springProfile name="prod,production">
  <!-- INFO 级别的标准日志 -->
  <logger name="com.nacos.mcp.router.v3" level="INFO"/>
  <logger name="org.springframework.ai" level="INFO"/>
  <logger name="com.alibaba.cloud.ai" level="INFO"/>
  <!-- Nacos 在生产环境降为 WARN 级别 -->
  <logger name="com.alibaba.nacos" level="WARN"/>
</springProfile>
```

## 特殊日志级别控制

为了优化性能和减少噪音，以下组件的日志级别被特别控制：

```xml
<!-- 性能敏感组件的日志级别控制 -->
<logger name="io.modelcontextprotocol.client.transport.WebFluxSseClientTransport" level="WARN"/>
<logger name="reactor.core.publisher.Operators" level="WARN"/>
<logger name="org.springframework.web.reactive.function.client" level="WARN"/>
<logger name="io.netty" level="WARN"/>
<logger name="reactor.netty" level="WARN"/>
```

## 使用方式

### 1. 在代码中使用
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MyComponent {
    public void doSomething() {
        log.info("这是信息日志");
        log.warn("这是警告日志");
        log.error("这是错误日志", exception);
    }
}
```

### 2. 环境激活
```bash
# 开发环境
java -jar mcp-router-v3.jar --spring.profiles.active=dev

# 生产环境
java -jar mcp-router-v3.jar --spring.profiles.active=prod
```

### 3. 动态配置
可以通过 JVM 参数覆盖配置：
```bash
# 修改根日志级别
java -jar mcp-router-v3.jar -Dlogging.level.root=DEBUG

# 修改特定包的日志级别
java -jar mcp-router-v3.jar -Dlogging.level.com.nacos.mcp.router.v3=TRACE
```

## 日志文件管理

### 文件结构
```
logs/
├── mcp-router-v3.log                    # 当前日志文件
├── mcp-router-v3-error.log             # 当前错误日志文件
├── mcp-router-v3.2025-08-01.log.gz     # 历史日志文件（压缩）
└── mcp-router-v3-error.2025-08-01.log.gz # 历史错误日志文件（压缩）
```

### 清理策略
- 自动删除30天前的日志文件
- 总日志大小超过限制时自动清理最旧的文件
- 支持手动清理：`find logs/ -name "*.log.gz" -mtime +30 -delete`

## 监控和调试

### 日志级别测试
运行测试验证配置：
```bash
mvn test -Dtest=LogbackConfigTest
```

### 日志文件监控
```bash
# 实时查看日志
tail -f logs/mcp-router-v3.log

# 查看错误日志
tail -f logs/mcp-router-v3-error.log

# 统计日志级别分布
grep -c "ERROR\|WARN\|INFO\|DEBUG" logs/mcp-router-v3.log
```

## 最佳实践

1. **开发时使用 DEBUG 级别** - 便于问题诊断
2. **生产时使用 INFO 级别** - 平衡信息量和性能
3. **定期清理日志文件** - 避免磁盘空间不足
4. **使用结构化日志** - 便于日志分析和监控
5. **避免过度日志** - 在循环和高频调用中谨慎使用日志

## 故障排除

### 常见问题

1. **日志文件没有生成**
   - 检查 `logs/` 目录是否存在且可写
   - 验证 logback-spring.xml 语法是否正确

2. **日志级别不正确**
   - 确认环境配置 (dev/prod) 是否正确
   - 检查是否有 JVM 参数覆盖了配置

3. **日志文件过大**
   - 调整滚动策略的大小限制
   - 考虑降低日志级别

4. **性能问题**
   - 检查是否有过多的 DEBUG 日志
   - 调整异步日志配置（如需要）

## 配置验证

项目包含了完整的测试用例 `LogbackConfigTest`，可以验证：
- Appender 配置正确性
- 日志级别设置
- 特定 Logger 配置
- 文件输出功能

运行测试：
```bash
mvn test -Dtest=LogbackConfigTest
```

测试通过表示 Logback 配置完全正常。 