# Nginx 配置说明

## 简单配置

这个 Nginx 配置非常简单，只做一件事：**负载均衡到 3 个后端实例**。

## 配置内容

- **upstream**: 定义 3 个后端服务器（8051, 8052, 8053）
- **ip_hash**: 会话粘性，确保同一客户端路由到同一实例
- **server**: 监听 80 端口，将所有请求转发到后端

## 使用方法

### 1. 复制配置文件

```bash
# macOS
sudo cp nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# Linux
sudo cp nginx/nginx.conf /etc/nginx/conf.d/mcp-bridge.conf
```

### 2. 测试配置

```bash
sudo nginx -t
```

### 3. 重载 Nginx

```bash
# macOS
sudo nginx -s reload

# Linux
sudo systemctl reload nginx
```

### 或者使用自动更新脚本

```bash
# 自动更新并重载配置
./scripts/update-nginx-config.sh
```

### 4. 配置本地域名（开发环境）

```bash
# 编辑 /etc/hosts
sudo vim /etc/hosts
# 添加: 127.0.0.1 mcp-bridge.local
```

## 验证

```bash
# 测试负载均衡
curl http://mcp-bridge.local/actuator/health
```

## 修改域名

编辑 `nginx.conf`，修改 `server_name` 为实际域名：

```nginx
server_name mcp-bridge.example.com;
```

