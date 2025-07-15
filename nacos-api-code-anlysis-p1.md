# Nacos API 详细调用链路分析报告

## 1. 应用启动流程分析

### 1.1 启动调用栈
从错误日志中可以看到完整的启动调用栈：

```
McpServerV2Application.main()
  └─ SpringApplication.run()
    └─ SpringApplication.refreshContext()
      └─ AbstractApplicationContext.refresh()
        └─ ReactiveWebServerApplicationContext.refresh()
          └─ AbstractApplicationContext.finishBeanFactoryInitialization()
            └─ DefaultListableBeanFactory.preInstantiateSingletons()
              └─ AbstractBeanFactory.getBean()
                └─ AbstractAutowireCapableBeanFactory.createBean()
                  └─ NacosMcpRegistryAutoConfiguration.nacosMcpRegisterAsync()
```

### 1.2 关键组件初始化顺序

1. Spring Context 初始化
2. Bean 工厂初始化
3. Nacos 配置加载
4. 数据库初始化
5. WebFlux 服务器启动
6. Nacos 服务注册

## 2. 错误分析与解决方案

### 2.1 DNS 解析错误
```
ERROR [single-1] i.n.r.d.DnsServerAddressStreamProviders : 
Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider
```

#### 解决方案：
1. 添加 MacOS DNS 解析依赖
```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-resolver-dns-native-macos</artifactId>
    <classifier>osx-x86_64</classifier>
    <version>4.1.107.Final</version>
</dependency>
```

2. 配置 JVM 参数
```bash
-Dio.netty.resolver.dns.macos.removeInvalidServers=true
```

### 2.2 端口占用问题
```
Web server failed to start. Port 8061 was already in use.
```

#### 解决方案：

1. 修改应用配置
```yaml
server:
  port: 8062  # 更改为未被占用的端口
spring:
  webflux:
    base-path: /mcp-server-v2
```

2. 使用随机端口
```yaml
server:
  port: 0  # 使用随机可用端口
```

3. 端口检查和释放脚本
```bash
#!/bin/bash
PORT=8061
PID=$(lsof -ti :$PORT)
if [ ! -z \"$PID\" ]; then
    echo \"Port $PORT is in use by process $PID\"
    kill -9 $PID
    echo \"Process $PID has been terminated\"
fi
```

### 2.3 数据库初始化问题

从日志可以看到数据库初始化过程：

```
DEBUG [actor-tcp-nio-2] o.s.r2dbc.connection.init.ScriptUtils : 
Executing SQL script from class path resource [schema.sql]
```

#### 优化建议：

1. 数据库配置
```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/mcp_db
    username: root
    password: root
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
```

2. 数据库初始化控制
```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
      continue-on-error: false
```

## 3. 组件生命周期管理

### 3.1 优雅关闭实现

从日志中可以看到系统关闭时的组件销毁顺序：

1. HttpClient 销毁
```
[HttpClientBeanHolder] Start destroying common HttpClient
[HttpClientBeanHolder] Completed destruction of HttpClient
```

2. 线程池关闭
```
[ThreadPoolManager] Start destroying ThreadPool
[ThreadPoolManager] Completed destruction of ThreadPool
```

3. 消息发布者关闭
```
[NotifyCenter] Start destroying Publisher
[NotifyCenter] Completed destruction of Publisher
```

#### 实现代码：

```java
@Configuration
public class GracefulShutdownConfig {
    
    @Bean
    public GracefulShutdown gracefulShutdown(
            NacosMcpProperties properties,
            NamingService namingService) {
        return new GracefulShutdown(properties, namingService);
    }
}

public class GracefulShutdown {
    private final NacosMcpProperties properties;
    private final NamingService namingService;
    
    @PreDestroy
    public void onShutdown() {
        // 1. 从 Nacos 注销服务
        try {
            namingService.deregisterInstance(
                properties.getServiceName(),
                properties.getGroup(),
                properties.getIp(),
                properties.getPort()
            );
        } catch (NacosException e) {
            log.error(\"Failed to deregister service\", e);
        }
        
        // 2. 等待当前请求处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 3. 关闭线程池
        ThreadPoolManager.shutdown();
        
        // 4. 关闭 HTTP 客户端
        HttpClientBeanHolder.shutdown();
        
        // 5. 关闭消息发布者
        NotifyCenter.shutdown();
    }
}
```

## 4. 性能优化建议

### 4.1 连接池配置

1. HTTP 客户端连接池
```java
@Configuration
public class HttpClientConfig {
    @Bean
    public HttpClient httpClient() {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(5))
                .addHandlerLast(new WriteTimeoutHandler(5)))
            .responseTimeout(Duration.ofSeconds(5));
    }
}
```

2. 线程池配置
```java
@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor mcpThreadPool() {
        return new ThreadPoolExecutor(
            10,                     // 核心线程数
            20,                     // 最大线程数
            60L,                    // 空闲线程存活时间
            TimeUnit.SECONDS,       // 时间单位
            new LinkedBlockingQueue<>(1000),  // 工作队列
            new ThreadFactoryBuilder()
                .setNameFormat(\"mcp-pool-%d\")
                .build(),           // 线程工厂
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }
}
```

### 4.2 缓存策略

1. 本地缓存配置
```java
@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, Object> mcpCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();
    }
}
```

2. 缓存使用示例
```java
@Service
public class McpCacheService {
    private final Cache<String, Object> cache;
    
    public Object getWithCache(String key, Supplier<Object> loader) {
        return cache.get(key, k -> loader.get());
    }
    
    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
```

## 5. 监控与告警

### 5.1 健康检查端点

```java
@Component
public class McpHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        try {
            // 检查 Nacos 连接
            boolean nacosHealth = checkNacosHealth();
            // 检查数据库连接
            boolean dbHealth = checkDatabaseHealth();
            // 检查线程池状态
            boolean threadPoolHealth = checkThreadPoolHealth();
            
            if (nacosHealth && dbHealth && threadPoolHealth) {
                return Health.up().build();
            }
            return Health.down()
                .withDetail(\"nacos\", nacosHealth)
                .withDetail(\"database\", dbHealth)
                .withDetail(\"threadPool\", threadPoolHealth)
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

### 5.2 指标收集

```java
@Configuration
public class MetricsConfig {
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    @Bean
    public McpMetrics mcpMetrics(MeterRegistry registry) {
        return new McpMetrics(registry);
    }
}

@Component
public class McpMetrics {
    private final Counter registrationCounter;
    private final Timer configFetchTimer;
    private final Gauge threadPoolActiveThreads;
    
    public McpMetrics(MeterRegistry registry) {
        this.registrationCounter = Counter
            .builder(\"mcp.service.registration\")
            .description(\"Number of service registrations\")
            .register(registry);
            
        this.configFetchTimer = Timer
            .builder(\"mcp.config.fetch\")
            .description(\"Time taken to fetch configuration\")
            .register(registry);
            
        this.threadPoolActiveThreads = Gauge
            .builder(\"mcp.threadpool.active\", threadPool, 
                ThreadPoolExecutor::getActiveCount)
            .description(\"Number of active threads\")
            .register(registry);
    }
}
```

## 6. 最佳实践建议

1. 配置管理
   - 使用配置中心管理配置
   - 实现配置热更新
   - 添加配置验证

2. 错误处理
   - 实现统一的异常处理
   - 添加重试机制
   - 实现服务降级

3. 性能优化
   - 使用连接池
   - 实现本地缓存
   - 优化线程池配置

4. 监控告警
   - 添加健康检查
   - 收集关键指标
   - 实现告警机制"}}

我已经创建了一个详细的分析报告，包含以下主要内容：

1. 应用启动流程分析
   - 完整的调用栈
   - 组件初始化顺序

2. 错误分析与解决方案
   - DNS 解析错误
   - 端口占用问题
   - 数据库初始化问题

3. 组件生命周期管理
   - 优雅关闭实现
   - 组件销毁顺序

4. 性能优化建议
   - 连接池配置
   - 缓存策略

5. 监控与告警
   - 健康检查端点
   - 指标收集

6. 最佳实践建议

需要我详细解释任何特定部分吗？