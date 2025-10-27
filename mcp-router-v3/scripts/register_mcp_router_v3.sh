#!/bin/bash

# Nacos 服务器地址
NACOS_ADDR="http://localhost:8848"
# Nacos 命名空间ID
NAMESPACE="public"
# Nacos 认证token（如有需要）
TOKEN=""

# 服务唯一ID、服务名、版本号
UUID="6eff2b4f-8c15-455e-86a0-302901241482"
SERVICE_NAME="mcp-router-v3"
VERSION="1.0.1"

# dataId
SERVER_DATAID="${UUID}-${VERSION}-mcp-server.json"
TOOLS_DATAID="${UUID}-${VERSION}-mcp-tools.json"
VERSIONS_DATAID="${UUID}-mcp-versions.json"

# group
SERVER_GROUP="mcp-server"
TOOLS_GROUP="mcp-tools"
VERSIONS_GROUP="mcp-server-versions"

# 1. 生成 mcp-server.json 内容
cat > mcp-server.json <<EOF
{
  "id": "$UUID",
  "name": "$SERVICE_NAME",
  "protocol": "mcp-sse",
  "frontProtocol": "mcp-sse",
  "description": "mcp-router-v3 服务",
  "enabled": true,
  "version": "$VERSION",
  "remoteServerConfig": {
    "serviceRef": {
      "serviceName": "$SERVICE_NAME",
      "groupName": "DEFAULT_GROUP",
      "namespaceId": "$NAMESPACE"
    },
    "exportPath": "/$SERVICE_NAME"
  },
  "toolsDescriptionRef": "$TOOLS_DATAID"
}
EOF

# 2. 生成 mcp-tools.json 内容（请根据实际工具补充）
cat > mcp-tools.json <<EOF
{
  "tools": [
    {
      "name": "getRouterInfo",
      "description": "获取路由信息",
      "inputSchema": {
        "type": "object",
        "properties": {
          "routerId": { "type": "string", "description": "路由ID" }
        },
        "required": ["routerId"],
        "additionalProperties": false
      }
    }
  ],
  "toolsMeta": {
    "getRouterInfo": { "enabled": true, "labels": ["router", "info"] }
  }
}
EOF

# 3. 生成 mcp-versions.json 内容（如有多个版本请补充 versionDetails）
cat > mcp-versions.json <<EOF
{
  "id": "$UUID",
  "name": "$SERVICE_NAME",
  "protocol": "mcp-sse",
  "frontProtocol": "mcp-sse",
  "description": "mcp-router-v3 服务",
  "enabled": true,
  "capabilities": ["TOOL"],
  "latestPublishedVersion": "$VERSION",
  "versionDetails": [
    {
      "version": "$VERSION",
      "release_date": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    }
  ]
}
EOF

# 4. 注册到 Nacos
echo "注册 mcp-server.json ..."
curl -X POST "$NACOS_ADDR/v1/cs/configs" \
  -d "dataId=$SERVER_DATAID" \
  -d "group=$SERVER_GROUP" \
  -d "namespaceId=$NAMESPACE" \
  -d "content=$(cat mcp-server.json)" \
  -H "Authorization: Bearer $TOKEN"

echo -e "\n注册 mcp-tools.json ..."
curl -X POST "$NACOS_ADDR/v1/cs/configs" \
  -d "dataId=$TOOLS_DATAID" \
  -d "group=$TOOLS_GROUP" \
  -d "namespaceId=$NAMESPACE" \
  -d "content=$(cat mcp-tools.json)" \
  -H "Authorization: Bearer $TOKEN"

echo -e "\n注册 mcp-versions.json ..."
curl -X POST "$NACOS_ADDR/v1/cs/configs" \
  -d "dataId=$VERSIONS_DATAID" \
  -d "group=$VERSIONS_GROUP" \
  -d "namespaceId=$NAMESPACE" \
  -d "content=$(cat mcp-versions.json)" \
  -H "Authorization: Bearer $TOKEN"

echo -e "\n注册完成！"
