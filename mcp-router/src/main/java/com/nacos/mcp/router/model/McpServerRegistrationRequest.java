package com.nacos.mcp.router.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * MCP服务器注册请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerRegistrationRequest {
    private String name;
    private String version;
    private String endpoint;
    private String description;
    private List<McpTool> tools;
    @Builder.Default
    private String transportType = "sse";
} 