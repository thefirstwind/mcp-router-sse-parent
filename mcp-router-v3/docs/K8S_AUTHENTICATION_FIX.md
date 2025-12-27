# Kubernetes Ingress 认证问题修复指南

## 问题描述

在生产环境中，MCP Inspector 连接 streamable 协议时出现 `401 Unauthorized` 错误，提示 "Authentication required. Use the session token shown in the console when starting the server"。

## 原因分析

这个问题通常与 Kubernetes Ingress 的认证配置有关，而不是应用层的 Spring Security。可能的原因包括：

1. **Ingress Controller 启用了默认认证**（如 Basic Auth、OAuth2 Proxy）
2. **Service Mesh（如 Istio）的认证策略**
3. **Ingress 注解中配置了认证相关设置**
4. **Ingress Controller 的默认安全策略**

## 解决方案

### 1. 检查 Ingress 配置

检查 `k8s/ingress.yaml` 中是否有以下认证相关的注解：

```yaml
# 如果存在以下注解，需要注释掉或删除：
nginx.ingress.kubernetes.io/auth-type: "basic"
nginx.ingress.kubernetes.io/auth-secret: "basic-auth"
nginx.ingress.kubernetes.io/auth-realm: "Authentication Required"
```

### 2. 检查 OAuth2 Proxy 或其他认证插件

如果集群中部署了 OAuth2 Proxy 或其他认证插件，需要：

1. **检查是否有认证相关的 Ingress 注解**：
   ```yaml
   # 如果存在，需要移除或配置为允许匿名访问
   nginx.ingress.kubernetes.io/auth-url: "https://oauth2-proxy.example.com/oauth2/auth"
   nginx.ingress.kubernetes.io/auth-signin: "https://oauth2-proxy.example.com/oauth2/start"
   ```

2. **或者为 MCP 端点创建单独的 Ingress**（不启用认证）：
   ```yaml
   apiVersion: networking.k8s.io/v1
   kind: Ingress
   metadata:
     name: mcp-router-ingress-public
     annotations:
       # 不包含任何认证相关注解
   ```

### 3. 检查 Service Mesh 认证策略

如果使用 Istio 或其他 Service Mesh，检查是否有认证策略：

```yaml
# 检查是否有类似以下的策略
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
spec:
  mtls:
    mode: STRICT  # 如果设置为 STRICT，可能需要调整
```

### 4. 更新 Ingress 配置（已实施）

已在 `k8s/ingress.yaml` 中：

1. ✅ 添加了 `X-Forwarded-Prefix: /mcp-bridge` 头
2. ✅ 更新了 path 为 `/mcp-bridge`
3. ✅ 添加了注释说明如何禁用认证

### 5. 验证步骤

1. **检查当前 Ingress 配置**：
   ```bash
   kubectl get ingress mcp-router-ingress -o yaml
   ```

2. **检查是否有认证相关的注解**：
   ```bash
   kubectl get ingress mcp-router-ingress -o jsonpath='{.metadata.annotations}' | grep -i auth
   ```

3. **检查 OAuth2 Proxy 或其他认证插件**：
   ```bash
   kubectl get pods -n default | grep -i oauth
   kubectl get ingress -A | grep -i oauth
   ```

4. **测试连接**：
   ```bash
   curl -v http://mcp-bridge.example.com/mcp-bridge/mcp/mcp-server-v6
   ```

### 6. 临时解决方案

如果需要快速验证，可以创建一个不启用认证的测试 Ingress：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mcp-router-ingress-test
  namespace: default
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
    nginx.ingress.kubernetes.io/proxy-buffering: "off"
    # 明确不启用任何认证
spec:
  ingressClassName: nginx
  rules:
    - host: mcp-bridge-test.example.com
      http:
        paths:
          - path: /mcp-bridge
            pathType: Prefix
            backend:
              service:
                name: mcp-router-service
                port:
                  number: 8080
```

## 常见问题

### Q: 如何确认是 Ingress 还是应用层的认证问题？

A: 
1. 直接访问 Pod IP（绕过 Ingress）：
   ```bash
   kubectl port-forward svc/mcp-router-service 8080:8080
   curl http://localhost:8080/mcp/mcp-server-v6
   ```
   如果直接访问 Pod 可以，但通过 Ingress 不行，说明是 Ingress 的问题。

2. 查看 Ingress Controller 日志：
   ```bash
   kubectl logs -n ingress-nginx <ingress-controller-pod> | grep -i "401\|auth"
   ```

### Q: 如何在保留其他路径认证的同时，允许 MCP 端点匿名访问？

A: 可以创建多个 Ingress 资源，或者使用路径级别的认证配置（取决于 Ingress Controller 的支持）。

## 相关文件

- `mcp-router-v3/k8s/ingress.yaml` - Ingress 配置文件
- `mcp-router-v3/k8s/service.yaml` - Service 配置
- `mcp-router-v3/k8s/deployment.yaml` - Deployment 配置

















