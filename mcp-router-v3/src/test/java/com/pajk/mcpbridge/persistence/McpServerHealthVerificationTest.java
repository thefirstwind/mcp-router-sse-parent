package com.pajk.mcpbridge.persistence;

import com.pajk.mcpbridge.core.McpRouterV3Application;
import com.pajk.mcpbridge.persistence.service.McpServerPersistenceService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;

/**
 * MCP服务器健康状态验证测试
 * 用于验证和修复特定服务器的健康状态
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = McpRouterV3Application.class)
public class McpServerHealthVerificationTest {
    
    private static final Logger log = LoggerFactory.getLogger(McpServerHealthVerificationTest.class);
    
    @Autowired
    private McpServerPersistenceService persistenceService;
    
    /**
     * 验证并修复 cf-server 和 mcp-server-v2-20250718 的健康状态
     */
    @Test
    public void testVerifyAndFixSpecificServers() {
        log.info("========================================");
        log.info("🔍 开始验证服务器健康状态");
        log.info("========================================");
        
        List<String> serverNames = Arrays.asList(
            "cf-server",
            "mcp-server-v2-20250718"
        );
        
        int fixedCount = persistenceService.verifyAndFixSpecificServers(serverNames);
        
        log.info("========================================");
        log.info("✅ 验证完成，共修复 {} 个服务器", fixedCount);
        log.info("========================================");
    }
    
    /**
     * 验证所有临时节点的健康状态
     */
    @Test
    public void testVerifyAllEphemeralNodes() {
        log.info("========================================");
        log.info("🔍 开始验证所有临时节点的健康状态");
        log.info("========================================");
        
        persistenceService.verifyAndMarkOfflineEphemeralNodes();
        
        log.info("========================================");
        log.info("✅ 验证完成");
        log.info("========================================");
    }
}

