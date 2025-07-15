
## 注册接口
curl -X POST "127.0.0.1:8848/nacos/v3/client/ns/instance" -d "serviceName=mcp-server-v2&ip=127.0.0.1&port=3306"


---------------------------------------------------
# 查询配置信息
# 入参： 命名空间，和 应用名
# 出参： dataId，appName，group
curl -X GET "http://localhost:8848/nacos/v2/cs/history/configs?pageNo=1&pageSize=100&namespaceId=public&appName=mcp-server-v2" -H "Content-Type: application/x-www-form-urlencoded"
{"code":0,"message":"success","data":[{"id":"0","dataId":"7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-tools.json","group":"mcp-tools","content":null,"md5":null,"encryptedDataKey":null,"tenant":"public","appName":"mcp-server-v2","type":"json","lastModified":1752548450169},{"id":"0","dataId":"7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-mcp-versions.json","group":"mcp-server-versions","content":null,"md5":null,"encryptedDataKey":null,"tenant":"public","appName":"mcp-server-v2","type":"json","lastModified":1752548450173},{"id":"0","dataId":"7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-server.json","group":"mcp-server","content":null,"md5":null,"encryptedDataKey":null,"tenant":"public","appName":"mcp-server-v2","type":"json","lastModified":1752548450176},{"id":"0","dataId":"be2bb98c-0adc-4927-9756-4f212aeeae53-1.0.1-SNAPSHOT-mcp-tools.json","group":"mcp-tools","content":null,"md5":null,"encryptedDataKey":null,"tenant":"public","appName":"webflux-mcp-server","type":"json","lastModified":1752548951680},{"id":"0","dataId":"be2bb98c-0adc-4927-9756-4f212aeeae53-mcp-versions.json","group":"mcp-server-versions","content":null,"md5":null,"encryptedDataKey":null,"tenant":"public","appName":"webflux-mcp-server","type":"json","lastModified":1752548951687},{"id":"0","dataId":"be2bb98c-0adc-4927-9756-4f212aeeae53-1.0.1-SNAPSHOT-mcp-server.json","group":"mcp-server","content":null,"md5":null,"encryptedDataKey":null,"tenant":"public","appName":"webflux-mcp-server","type":"json","lastModified":1752548951694}]}



---------------------------------------------------

# 根据dataId获取当前可用版本信息的配置的详细信息，groupName=mcp-server-versions
## 入参：dataId，groupName
## 出参：data.content
### version结构：
{
    "id": "7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5",
    "name": "mcp-server-v2",
    "protocol": "mcp-sse",
    "frontProtocol": "mcp-sse",
    "description": "mcp-server-v2",
    "enabled": true,
    "capabilities": [
        "TOOL"
    ],
    "latestPublishedVersion": "1.0.1",
    "versionDetails": [
        {
            "version": "1.0.1",
            "release_date": "2025-07-15T03:00:50Z"
        }
    ]
}
## 实际方法举例

## 实际方法举例
curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-mcp-versions.json&groupName=mcp-server-versions'

## 执行结果
{"code":0,"message":"success","data":{"resultCode":200,"errorCode":0,"message":null,"requestId":null,"content":"{\"id\":\"7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5\",\"name\":\"mcp-server-v2\",\"protocol\":\"mcp-sse\",\"frontProtocol\":\"mcp-sse\",\"description\":\"mcp-server-v2\",\"enabled\":true,\"capabilities\":[\"TOOL\"],\"latestPublishedVersion\":\"1.0.1\",\"versionDetails\":[{\"version\":\"1.0.1\",\"release_date\":\"2025-07-15T03:00:50Z\"}]}","encryptedDataKey":null,"contentType":"json","md5":"41963bb16f3b1771bb3a7ef3c89a0b29","lastModified":1752548450173,"tag":null,"beta":false,"success":true}}


---------------------------------------------------
# 根据dataId获取当前可用版本信息的配置的详细信息，groupName=mcp-server
## 入参：dataId，groupName
## 出参：data.content.tools
### version结构：
{
    "id": "7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5",
    "name": "mcp-server-v2",
    "protocol": "mcp-sse",
    "frontProtocol": "mcp-sse",
    "description": "mcp-server-v2",
    "versionDetail": {
        "version": "1.0.1",
        "release_date": "2025-07-15T03:00:50Z"
    },
    "remoteServerConfig": {
        "serviceRef": {
            "namespaceId": "public",
            "groupName": "mcp-server",
            "serviceName": "mcp-server-v2"
        },
        "exportPath": "/sse"
    },
    "enabled": true,
    "capabilities": [
        "TOOL"
    ],
    "toolsDescriptionRef": "7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-tools.json"
}

curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-server.json&groupName=mcp-server'

## 执行结果
{"code":0,"message":"success","data":{"resultCode":200,"errorCode":0,"message":null,"requestId":null,"content":"{\"id\":\"7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5\",\"name\":\"mcp-server-v2\",\"protocol\":\"mcp-sse\",\"frontProtocol\":\"mcp-sse\",\"description\":\"mcp-server-v2\",\"versionDetail\":{\"version\":\"1.0.1\",\"release_date\":\"2025-07-15T03:00:50Z\"},\"remoteServerConfig\":{\"serviceRef\":{\"namespaceId\":\"public\",\"groupName\":\"mcp-server\",\"serviceName\":\"mcp-server-v2\"},\"exportPath\":\"/sse\"},\"enabled\":true,\"capabilities\":[\"TOOL\"],\"toolsDescriptionRef\":\"7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-tools.json\"}","encryptedDataKey":null,"contentType":"json","md5":"0e716166b48199f9a737a765e3f10bca","lastModified":1752548450176,"tag":null,"beta":false,"success":true}}

---------------------------------------------------

# 根据dataId获取tools的配置的详细信息，groupName=mcp-tools
## 入参：dataId，groupName
## 出参：data.content.tools
### tools结构：
{
    "name": "deletePerson",
    "description": "Delete a person from the database",
    "inputSchema": {
        "type": "object",
        "properties": {
            "id": {
                "type": "integer",
                "format": "int64",
                "description": "Person's ID"
            }
        },
        "required": [
            "id"
        ],
        "additionalProperties": false
    }
}
## 实际方法举例
curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-tools.json&groupName=mcp-tools'
## 执行结果
{"code":0,"message":"success","data":{"resultCode":200,"errorCode":0,"message":null,"requestId":null,"content":"{\"tools\":[{\"name\":\"deletePerson\",\"description\":\"Delete a person from the database\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"format\":\"int64\",\"description\":\"Person's ID\"}},\"required\":[\"id\"],\"additionalProperties\":false}},{\"name\":\"getPersonById\",\"description\":\"Get a person by their ID\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"format\":\"int64\",\"description\":\"Person's ID\"}},\"required\":[\"id\"],\"additionalProperties\":false}},{\"name\":\"getAllPersons\",\"description\":\"Get all persons from the database\",\"inputSchema\":{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}},{\"name\":\"get_system_info\",\"description\":\"Get system information\",\"inputSchema\":{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}},{\"name\":\"list_servers\",\"description\":\"List all registered servers\",\"inputSchema\":{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}},{\"name\":\"addPerson\",\"description\":\"Add a new person to the database\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"firstName\":{\"type\":\"string\",\"description\":\"Person's first name\"},\"lastName\":{\"type\":\"string\",\"description\":\"Person's last name\"},\"age\":{\"type\":\"integer\",\"format\":\"int32\",\"description\":\"Person's age\"},\"nationality\":{\"type\":\"string\",\"description\":\"Person's nationality\"},\"gender\":{\"type\":\"string\",\"description\":\"Person's gender (MALE, FEMALE, OTHER)\"}},\"required\":[\"firstName\",\"lastName\",\"age\",\"nationality\",\"gender\"],\"additionalProperties\":false}}],\"toolsMeta\":{}}","encryptedDataKey":null,"contentType":"json","md5":"e955eb18dd115772721583b5c58b0ccc","lastModified":1752548450169,"tag":null,"beta":false,"success":true}}

---------------------------------------------------
#查询实例列表
## 入参： serviceName，groupName
## 出参： ip，健康度，权重，集群节点等
curl -X GET "http://localhost:8848/nacos/v2/ns/instance/list?serviceName=mcp-server-v2&groupName=mcp-server"
{"code":0,"message":"success","data":{"name":"mcp-server@@mcp-server-v2","groupName":"mcp-server","clusters":"","cacheMillis":10000,"hosts":[{"instanceId":"192.168.0.103#8062#null#mcp-server@@mcp-server-v2","ip":"192.168.0.103","port":8062,"weight":1.0,"healthy":true,"enabled":true,"ephemeral":true,"clusterName":"DEFAULT","serviceName":"mcp-server@@mcp-server-v2","metadata":{},"ipDeleteTimeout":30000,"instanceIdGenerator":"simple","instanceHeartBeatTimeOut":15000,"instanceHeartBeatInterval":5000}],"lastRefTime":1752552537798,"checksum":"","allIps":false,"reachProtectionThreshold":false,"valid":true}}