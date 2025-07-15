package com.nacos.mcp.router.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 信息实体类
 */
@Data
public class McpServerInfo {
    private String name;
    private String version;
    private String description;
    private boolean enabled;
    private List<McpToolInfo> tools;
    private Map<String, String> metadata;
    private String instanceId;
    private String ip;
    private int port;
    private boolean healthy;
    private long lastUpdateTime;
} 