package com.nacos.mcp.router.model;

import lombok.Data;
import java.util.List;

@Data
public class McpServerBasicInfo {
    private String name;
    private String version;
    private String protocol;
    private EndpointReference endpointReference;
    private RemoteServerConfig remoteServerConfig;
    private List<McpTool> tools;

    @Data
    public static class EndpointReference {
        private String serviceName;
        private String groupName;
    }

    @Data
    public static class RemoteServerConfig {
        private String ssePath;
    }
} 