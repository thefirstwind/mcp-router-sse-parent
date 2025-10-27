#!/bin/bash

# 获取本地IP地址
LOCAL_IP=$(hostname -I | awk '{print $1}')

echo "🔧 Starting mcp-server-v5 with IP: $LOCAL_IP"

# 通过 JVM 参数设置 IP 地址
mvn spring-boot:run \
  -Dspring.cloud.client.ip-address=$LOCAL_IP \
  -Dspring.cloud.client.hostname=$LOCAL_IP \
  -Dserver.address=$LOCAL_IP \
  -Dnacos.client.ip=$LOCAL_IP \
  -Dnacos.client.host=$LOCAL_IP \
  -Dcom.alibaba.nacos.client.naming.tls.enable=false \
  -Dcom.alibaba.nacos.client.naming.push.enabled=true \
  -Dcom.alibaba.nacos.client.naming.client.heart.beat.interval=5000 \
  -Dcom.alibaba.nacos.client.naming.client.heart.beat.timeout=15000 