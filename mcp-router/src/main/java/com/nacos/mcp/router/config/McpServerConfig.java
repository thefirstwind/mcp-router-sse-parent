package com.nacos.mcp.router.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MCP Server Configuration
 * Configures the MCP server with proper protocol support
 */
@Configuration
@Slf4j
@EnableConfigurationProperties(McpServerConfig.McpProperties.class)
public class McpServerConfig {

    @Bean
    public ObjectMapper mcpObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 启用JSON注释支持
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
        
        // 增强错误容忍性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        // 宽松的JSON解析
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        
        log.info("✅ Configured enhanced ObjectMapper with JSON comment support and error tolerance");
        return mapper;
    }

    /**
     * MCP configuration properties
     */
    @ConfigurationProperties(prefix = "mcp")
    public static class McpProperties {
        
        private Transport transport = new Transport();
        private Server server = new Server();
        
        public Transport getTransport() {
            return transport;
        }
        
        public void setTransport(Transport transport) {
            this.transport = transport;
        }
        
        public Server getServer() {
            return server;
        }
        
        public void setServer(Server server) {
            this.server = server;
        }
        
        public static class Transport {
            private String mode = "sse"; // sse or stdio
            private String endpoint = "/mcp/sse";
            
            public String getMode() {
                return mode;
            }
            
            public void setMode(String mode) {
                this.mode = mode;
            }
            
            public String getEndpoint() {
                return endpoint;
            }
            
            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }
        }
        
        public static class Server {
            private String name = "nacos-mcp-router";
            private String version = "1.0.0";
            private Capabilities capabilities = new Capabilities();
            
            public String getName() {
                return name;
            }
            
            public void setName(String name) {
                this.name = name;
            }
            
            public String getVersion() {
                return version;
            }
            
            public void setVersion(String version) {
                this.version = version;
            }
            
            public Capabilities getCapabilities() {
                return capabilities;
            }
            
            public void setCapabilities(Capabilities capabilities) {
                this.capabilities = capabilities;
            }
        }
        
        public static class Capabilities {
            private boolean tools = true;
            private boolean resources = true;
            private boolean prompts = true;
            private boolean logging = true;
            
            public boolean isTools() {
                return tools;
            }
            
            public void setTools(boolean tools) {
                this.tools = tools;
            }
            
            public boolean isResources() {
                return resources;
            }
            
            public void setResources(boolean resources) {
                this.resources = resources;
            }
            
            public boolean isPrompts() {
                return prompts;
            }
            
            public void setPrompts(boolean prompts) {
                this.prompts = prompts;
            }
            
            public boolean isLogging() {
                return logging;
            }
            
            public void setLogging(boolean logging) {
                this.logging = logging;
            }
        }
    }
} 