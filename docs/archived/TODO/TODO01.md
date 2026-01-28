逐一验证所有测试case，去掉所有mock数据，必须使用真实数据验证
nacos本地已经启动，nacos.ai.mcp.registry.enabled=true 配置已经设置，支持mcp注册，
通过以下两种方式可以访问
curl -X GET 'http://localhost:8848/nacos/v1/console/server/state' -H 'Authorization: Key-Value nacos:nacos'
curl -X GET 'http://localhost:8848/nacos/v1/console/server/state' -H 'Authorization: Bearer VGhpc0lzTXlDdXN0b21TZWNyZXRLZXkwMTIzNDU2Nzg='
项目需要依赖spring-ai，不需要openai，用的是deepseek
