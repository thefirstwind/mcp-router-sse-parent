package com.nacos.mcp.router.service;

import com.nacos.mcp.router.model.McpServerBasicInfo;
import java.util.List;

/**
 * MCP服务器发现服务
 * 负责从Nacos中发现和获取MCP服务器信息
 */
public interface McpServerDiscoveryService {
    
    /**
     * 列出所有已注册的MCP服务器
     *
     * @return MCP服务器基本信息列表
     */
    List<McpServerBasicInfo> listMcpServers();
    
    /**
     * 根据服务名称获取MCP服务器信息
     *
     * @param serviceName 服务名称
     * @return MCP服务器基本信息
     */
    McpServerBasicInfo getMcpServer(String serviceName);
} 