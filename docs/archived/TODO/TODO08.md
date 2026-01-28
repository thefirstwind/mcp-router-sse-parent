项目实现必须参考： https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-mcp/ 


其他参考文档：
https://nacos.io/en/blog/nacos-gvr7dx_awbbpb_gg16sv97bgirkixe/?spm=5238cd80.7f2fc5d1.0.0.642e5f9aoZLhEW&source=blog
https://nacos.io/en/blog/nacos-gvr7dx_awbbpb_qdi918msnqbvonx2/?spm=5238cd80.7f2fc5d1.0.0.642e5f9aoZLhEW&source=blog
https://modelcontextprotocol.io/sdk/java/mcp-overview
https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html
https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html
https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-mcp/ 


mcp-server-v2 中放开tool和toolParam注释，需要声明mcp server，这是核心逻辑
mcp-server-v2启动后，通过mcp-router注册到nacos中
mcp-client调用服务时，通过mcp-router查询nacos上的注册信息，寻找对应的mcp server服务
mcp-router端口 8050， mcp-server-v2端口 8061，mcp-client端口8070，不要修改。 
项目都使用响应式编程，依赖的jar包不要使用阻塞式
编译时，使用以下命令
    mvn -f mcp-server-v2/pom.xml clean install -nsu
    mvn -f mcp-router/pom.xml clean install -nsu
    mvn -f mcp-client/pom.xml clean install -nsu
