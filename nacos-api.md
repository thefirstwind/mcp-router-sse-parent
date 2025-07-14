## 获取配置接口
curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=c37fcc06-f691-429d-856b-b501c4b016ac-mcp-versions.json&groupName=mcp-server-versions'
{"code":0,"message":"success","data":{"resultCode":200,"errorCode":0,"message":null,"requestId":null,"content":"{\"id\":\"c37fcc06-f691-429d-856b-b501c4b016ac\",\"name\":\"mcp-server-v2\",\"protocol\":\"mcp-sse\",\"frontProtocol\":\"mcp-sse\",\"description\":\"mcp-server-v2\",\"enabled\":true,\"capabilities\":[],\"latestPublishedVersion\":\"1.0.1-SNAPSHOT\",\"versionDetails\":[{\"version\":\"1.0.1-SNAPSHOT\",\"release_date\":\"2025-07-14T10:39:59Z\"}]}","encryptedDataKey":"","contentType":"json","md5":"f21717203e181211252515a6e5c7cc76","lastModified":1752489599516,"tag":null,"beta":false,"success":true}}%    

## 注册接口
curl -X POST "127.0.0.1:8848/nacos/v3/client/ns/instance" -d "serviceName=mcp-server-v2&ip=127.0.0.1&port=3306"

## 查询指定服务的实例列表
