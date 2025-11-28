#!/bin/bash
# 快速访问脚本 - 使用 --resolve 绕过 DNS 解析延迟
# 用法: ./fast-access.sh [curl 参数] http://mcp-bridge.local/...

# 提取 URL
URL="$1"
shift

# 使用 --resolve 强制解析到 127.0.0.1
curl --resolve mcp-bridge.local:80:127.0.0.1 "$URL" "$@"



