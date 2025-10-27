package com.pajk.mcpbridge.core.config;

/**
 * MCP Nacos配置常量
 */
public class McpNacosConstants {
    
    // 组名常量
    public static final String SERVER_GROUP = "mcp-server";
    public static final String TOOLS_GROUP = "mcp-tools";
    public static final String VERSIONS_GROUP = "mcp-server-versions";
    
    // 配置后缀常量
    public static final String SERVER_CONFIG_SUFFIX = "-mcp-server.json";
    public static final String TOOLS_CONFIG_SUFFIX = "-mcp-tools.json";
    public static final String VERSIONS_CONFIG_SUFFIX = "-mcp-versions.json";
    
    // 服务名后缀
    public static final String SERVER_NAME_SUFFIX = "-server";
    
    // 默认超时时间
    public static final long DEFAULT_TIMEOUT = 3000L;
    
    // 版本信息配置key
    public static final String VERSION_KEY = "version";
    public static final String DESCRIPTION_KEY = "description";
    public static final String PROTOCOL_KEY = "protocol";
    public static final String FRONT_PROTOCOL_KEY = "frontProtocol";
    public static final String ENABLED_KEY = "enabled";
    public static final String CAPABILITIES_KEY = "capabilities";
    public static final String LATEST_PUBLISHED_VERSION_KEY = "latestPublishedVersion";
    
    // 默认值
    public static final String DEFAULT_PROTOCOL = "mcp-sse";
    public static final String DEFAULT_SSE_ENDPOINT = "/sse";
    public static final String[] DEFAULT_CAPABILITIES = {"TOOL"};
    
    // 配置分组
    public static final String CONFIG_GROUP = "DEFAULT_GROUP";
    
    private McpNacosConstants() {
        // 私有构造函数，防止实例化
    }
} 