#!/bin/bash

NACOS_ADDR="http://localhost:8848"
NAMESPACE="public"
TOKEN=""

UUID="6eff2b4f-8c15-455e-86a0-302901241482"
VERSION="1.0.1"

SERVER_DATAID="${UUID}-${VERSION}-mcp-server.json"
TOOLS_DATAID="${UUID}-${VERSION}-mcp-tools.json"
VERSIONS_DATAID="${UUID}-mcp-versions.json"

SERVER_GROUP="mcp-server"
TOOLS_GROUP="mcp-tools"
VERSIONS_GROUP="mcp-server-versions"

echo "查询 mcp-server.json ..."
curl -G "$NACOS_ADDR/v1/cs/configs" \
  --data-urlencode "dataId=$SERVER_DATAID" \
  --data-urlencode "group=$SERVER_GROUP" \
  --data-urlencode "namespaceId=$NAMESPACE" \
  -H "Authorization: Bearer $TOKEN"

echo -e "\n查询 mcp-tools.json ..."
curl -G "$NACOS_ADDR/v1/cs/configs" \
  --data-urlencode "dataId=$TOOLS_DATAID" \
  --data-urlencode "group=$TOOLS_GROUP" \
  --data-urlencode "namespaceId=$NAMESPACE" \
  -H "Authorization: Bearer $TOKEN"

echo -e "\n查询 mcp-versions.json ..."
curl -G "$NACOS_ADDR/v1/cs/configs" \
  --data-urlencode "dataId=$VERSIONS_DATAID" \
  --data-urlencode "group=$VERSIONS_GROUP" \
  --data-urlencode "namespaceId=$NAMESPACE" \
  -H "Authorization: Bearer $TOKEN"
