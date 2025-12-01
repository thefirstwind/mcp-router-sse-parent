# 简单的 Nginx 负载均衡配置

## 配置说明

只需要一个简单的 Nginx 配置，将请求负载均衡到 3 个后端实例。

## 配置文件

`nginx/nginx.conf` - 只有 20 行，非常简单：

```nginx
upstream mcp_router_backend {
    ip_hash;  # 会话粘性
    server 127.0.0.1:8051;
    server 127.0.0.1:8052;
    server 127.0.0.1:8053;
}

server {
    listen 80;
    server_name mcp-bridge.local;
    
    location / {
        proxy_pass http://mcp_router_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 快速部署

### 1. 启动后端实例

```bash
./scripts/start-instances.sh start
```

### 2. 配置 Nginx

```bash
# 复制配置
sudo cp nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf  # macOS
# 或
sudo cp nginx/nginx.conf /etc/nginx/conf.d/mcp-bridge.conf  # Linux

# 测试配置
sudo nginx -t

# 重载
sudo nginx -s reload
```

### 3. 配置本地域名（开发环境）

```bash
echo "127.0.0.1 mcp-bridge.local" | sudo tee -a /etc/hosts
```

### 4. 测试

```bash
curl http://mcp-bridge.local/actuator/health
```

## 完成！

就这么简单，Nginx 会自动将请求负载均衡到 3 个后端实例。








