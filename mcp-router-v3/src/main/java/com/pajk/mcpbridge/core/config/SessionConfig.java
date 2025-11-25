package com.pajk.mcpbridge.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpSessionProperties.class)
public class SessionConfig {
}

