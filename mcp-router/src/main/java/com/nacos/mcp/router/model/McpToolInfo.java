package com.nacos.mcp.router.model;

import lombok.Data;
import java.util.Map;

/**
 * MCP Tool 信息实体类
 */
@Data
public class McpToolInfo {
    private String name;
    private String description;
    private String version;
    private Map<String, Object> parameters;
    private boolean enabled;
} 