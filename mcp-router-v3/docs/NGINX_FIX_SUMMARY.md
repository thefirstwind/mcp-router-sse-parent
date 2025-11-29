# Nginx 启动问题修复总结

## 问题

Nginx 启动不正常，配置文件测试失败。

## 根本原因

Nginx 配置文件缺少必需的结构块：
1. ❌ 缺少 `events` 块
2. ❌ 缺少 `http` 块（`upstream` 必须在 `http` 块内）
3. ⚠️ 日志文件路径权限问题

## 修复内容

### 1. 添加 `events` 块 ✅

```nginx
events {
    worker_connections 1024;
}
```

### 2. 添加 `http` 块 ✅

将 `upstream` 和 `server` 块放在 `http` 块内：

```nginx
http {
    # 日志配置
    error_log logs/nginx-error.log warn;
    access_log logs/nginx-access.log;
    
    # PID 文件
    pid logs/nginx.pid;
    
    upstream mcp_router_backend {
        # ...
    }
    
    server {
        # ...
    }
}
```

### 3. 修复日志路径 ✅

将日志文件路径从系统目录改为项目目录：
- 之前: `/opt/homebrew/var/log/nginx/error.log`（需要 root 权限）
- 现在: `logs/nginx-error.log`（项目目录，无需特殊权限）

## 修复后的配置文件结构

```nginx
# 注释和说明

events {
    worker_connections 1024;
}

http {
    error_log logs/nginx-error.log warn;
    access_log logs/nginx-access.log;
    pid logs/nginx.pid;
    
    upstream mcp_router_backend {
        ip_hash;
        server 127.0.0.1:8051;
        server 127.0.0.1:8052;
        server 127.0.0.1:8053;
    }
    
    server {
        listen 80;
        listen [::]:80;
        server_name mcp-bridge.test;
        
        location / {
            # ... 配置 ...
        }
    }
}
```

## 验证

### 测试配置文件

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
nginx -t -c "$(pwd)/nginx/nginx.conf"
```

应该看到：
```
nginx: the configuration file ... syntax is ok
nginx: configuration file ... test is successful
```

### 启动 Nginx

```bash
# 启动（前台模式，用于测试）
nginx -c "$(pwd)/nginx/nginx.conf"

# 或者后台启动
nginx -c "$(pwd)/nginx/nginx.conf" -g "daemon on;"
```

### 重新加载配置（如果 Nginx 已在运行）

```bash
nginx -s reload -c "$(pwd)/nginx/nginx.conf"
```

## 注意事项

1. **日志文件位置**: 日志文件现在在项目 `logs/` 目录下，确保目录存在：
   ```bash
   mkdir -p logs
   ```

2. **PID 文件**: PID 文件也在项目 `logs/` 目录下，用于管理 Nginx 进程。

3. **权限**: 如果使用项目目录，通常不需要 root 权限，但如果要监听 80 端口，仍然需要 root 权限。

4. **端口 80**: 如果要以非 root 用户运行，可以改为监听其他端口（如 8080），然后使用端口转发。

## 相关文件

- `nginx/nginx.conf` - Nginx 配置文件
- `scripts/check-nginx.sh` - Nginx 状态检查脚本
- `logs/nginx-error.log` - 错误日志
- `logs/nginx-access.log` - 访问日志




