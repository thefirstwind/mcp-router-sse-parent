package com.nacos.mcp.router.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP Server Model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServer {

    /**
     * Server name
     */
    private String name;

    /**
     * Server version
     */
    private String version;

    /**
     * Server description
     */
    private String description;

    /**
     * Server provider
     */
    private String provider;

    /**
     * Transport type (e.g., HTTP, gRPC)
     */
    private String transportType;

    /**
     * Server endpoint URL
     */
    private String endpoint;

    /**
     * Installation command for the server (optional)
     */
    private String installCommand;

    /**
     * Server IP
     */
    private String ip;

    /**
     * Server port
     */
    private int port;

    /**
     * Server status
     */
    private ServerStatus status;

    /**
     * Last heartbeat time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastHeartbeat;

    /**
     * Available tools
     */
    private List<McpTool> tools;

    /**
     * Server metadata
     */
    private Map<String, Object> metadata;

    /**
     * Registration time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime registrationTime;

    /**
     * Last update time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastUpdateTime;

    /**
     * Relevance score (used in search results)
     */
    private Double relevanceScore;

    public enum ServerStatus {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN,
        ERROR,
        CONNECTED,
        DISCONNECTED,
        REGISTERED
    }
} 