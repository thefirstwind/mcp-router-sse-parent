package com.nacos.mcp.router.v2;

import com.nacos.mcp.router.v2.controller.HealthCheckControllerTest;
import com.nacos.mcp.router.v2.controller.HealthControllerTest;
import com.nacos.mcp.router.v2.controller.McpRouterControllerTest;
import com.nacos.mcp.router.v2.controller.McpServerControllerTest;
import com.nacos.mcp.router.v2.integration.EndToEndRoutingTest;
import com.nacos.mcp.router.v2.integration.FaultRecoveryTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * MCP Router V2 测试套件
 * 运行所有测试用例的集合
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        // 控制器测试
        HealthControllerTest.class,
        McpServerControllerTest.class,
        McpRouterControllerTest.class,
        HealthCheckControllerTest.class,
        
        // 集成测试
        EndToEndRoutingTest.class,
        FaultRecoveryTest.class
})
public class McpRouterV2TestSuite {
    // 测试套件类，不需要实现内容
} 