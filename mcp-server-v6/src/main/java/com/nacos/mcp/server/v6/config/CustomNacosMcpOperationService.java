package com.nacos.mcp.server.v6.config;

import com.alibaba.cloud.ai.mcp.nacos.service.NacosMcpOperationService;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

/**
 * 自定义 Nacos MCP 操作服务
 * 扩展原有的 NacosMcpOperationService，增加对已存在服务的自动更新支持
 */
@Slf4j
public class CustomNacosMcpOperationService extends NacosMcpOperationService {

    private final AiMaintainerService aiMaintainerService;
    private final String namespace;

    public CustomNacosMcpOperationService(Properties nacosProperties) throws NacosException {
        super(nacosProperties);
        // 创建独立的 AiMaintainerService 实例用于执行更新操作
        this.aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(nacosProperties);
        this.namespace = nacosProperties.getProperty(PropertyKeyConst.NAMESPACE, "public");
    }

    @Override
    public boolean createMcpServer(String mcpName, McpServerBasicInfo serverSpec, McpToolSpecification toolSpec,
                                   McpEndpointSpec endpointSpec) throws NacosException {
        try {
            // 尝试调用父类的创建逻辑
            return super.createMcpServer(mcpName, serverSpec, toolSpec, endpointSpec);
        } catch (NacosException e) {
            // 捕获 20005 (资源冲突) 错误
            if (e.getErrCode() == 20005 || (e.getMessage() != null && e.getMessage().contains("existed"))) {
                log.info("ℹ️ MCP Server '{}' already exists (Code 20005). Switching to UPDATE mode.", mcpName);
                
                // 确保 namespaceId 正确
                endpointSpec.getData().put("namespaceId", this.namespace);
                
                // 调用 updateMcpServer
                // 注意：isLatest 参数设置为 true，表示更新为最新版本
                return aiMaintainerService.updateMcpServer(this.namespace, mcpName, true, serverSpec, toolSpec, endpointSpec);
            }
            throw e;
        }
    }
}
