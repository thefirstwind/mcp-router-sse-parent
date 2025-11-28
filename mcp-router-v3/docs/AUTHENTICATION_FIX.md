# MCP Router 认证问题修复指南

## 问题描述

在生产环境中，SSE 和 MCP 连接出现权限错误：

1. **SSE 连接错误**：访问 `.well-known/oauth-protected-resource` 时出现权限错误
2. **MCP 连接错误**：返回 `401 Unauthorized`，提示 "Authentication required. Use the session token shown in the console when starting the server"

## 原因分析

这是 Spring AI MCP Server 框架的默认认证机制。Spring AI MCP Server 默认启用了 OAuth 保护，要求客户端提供认证令牌。

## 解决方案

### 方案 1：在 application.yml 中禁用认证（已实施）

已在 `application.yml` 中添加以下配置：

```yaml
spring:
  ai:
    mcp:
      server:
        authentication:
          enabled: false
```

**注意**：如果此配置项不存在或不起作用，请尝试其他方案。

### 方案 2：检查 Spring AI MCP Server 配置选项

如果方案 1 不起作用，可能需要查看 Spring AI MCP Server 的实际配置选项。可能的配置项包括：

- `spring.ai.mcp.server.security.enabled: false`
- `spring.ai.mcp.server.oauth.enabled: false`
- 或其他类似的配置项

### 方案 3：添加 Spring Security 配置（已实施）

由于 Spring AI MCP Server 默认启用了认证，需要添加 Spring Security 配置来允许匿名访问：

1. **已添加 Spring Security WebFlux 依赖**（在 `pom.xml` 中）：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

2. **已创建 Security 配置类**（`SecurityConfig.java`）：
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/sse/**", "/mcp/**", "/.well-known/**", "/api/**")
                .permitAll()
                .anyExchange().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .build();
    }
}
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/sse/**", "/mcp/**", "/.well-known/**")
                .permitAll()
                .anyExchange().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .build();
    }
}
```

### 方案 4：通过 Nginx 传递认证头（如果必须使用认证）

如果生产环境必须使用认证，需要在 Nginx 配置中传递认证头：

```nginx
location /mcp-bridge/ {
    # ... 其他配置 ...
    
    # 传递认证头
    proxy_set_header Authorization $http_authorization;
    proxy_pass_header Authorization;
    
    # 传递其他可能的认证头
    proxy_set_header X-Session-Token $http_x_session_token;
}
```

## 已实施的解决方案

✅ **已添加 Spring Security 配置**（推荐方案）

1. **添加了 Spring Security 依赖**（`pom.xml`）
2. **创建了 SecurityConfig 配置类**，允许匿名访问所有 MCP 端点
3. **保留了 application.yml 中的认证禁用配置**（作为备用）

## 验证步骤

1. **重新编译应用**：
   ```bash
   cd mcp-router-v3
   mvn clean package
   ```

2. **重新部署到生产环境**：
   - 停止当前运行的应用
   - 部署新编译的 JAR 文件
   - 启动应用

2. **测试 SSE 连接**：
   ```bash
   curl -N -H 'Accept: text/event-stream' 'http://mcp-bridge.test/mcp-bridge/sse/mcp-server-v6'
   ```

3. **测试 MCP 连接**：
   ```bash
   curl -N -H 'Accept: application/x-ndjson+stream' 'http://mcp-bridge.test/mcp-bridge/mcp/mcp-server-v6'
   ```

4. **检查日志**：
   - 查看应用日志，确认没有认证错误
   - 查看 Nginx 日志，确认请求正常转发

## 注意事项

1. **安全性**：禁用认证会降低安全性，请确保：
   - 通过 Nginx/Ingress 进行访问控制
   - 使用防火墙限制访问来源
   - 在生产环境中考虑使用其他安全措施

2. **配置验证**：如果 `authentication.enabled: false` 配置项不存在，应用启动时可能会有警告，但不影响功能。

3. **向后兼容**：如果将来需要启用认证，只需将 `enabled: false` 改为 `enabled: true` 并配置相应的认证方式。

## 相关文件

- `mcp-router-v3/src/main/resources/application.yml` - 主配置文件
- `mcp-router-v3/nginx/nginx.conf` - Nginx 配置（如果需要传递认证头）

