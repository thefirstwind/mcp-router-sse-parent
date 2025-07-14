package com.nacos.mcp.router.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MCP Resource Model - aligned with MCP specification
 * Resources are read-only data sources that can be accessed by MCP clients
 * Based on: https://modelcontextprotocol.io/specification/basic/resources
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResource {

    /**
     * Unique resource URI (required)
     */
    @JsonProperty("uri")
    private String uri;

    /**
     * Human-readable name (optional)
     */
    @JsonProperty("name")
    private String name;

    /**
     * Resource description (optional)
     */
    @JsonProperty("description")
    private String description;

    /**
     * MIME type of the resource (optional)
     */
    @JsonProperty("mimeType")
    private String mimeType;

    /**
     * Resource content (for read responses)
     */
    @JsonProperty("contents")
    private String contents;

    /**
     * Resource metadata (optional)
     */
    @JsonProperty("annotations")
    private Map<String, Object> annotations;

    /**
     * Last modified timestamp
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastModified;

    /**
     * Resource size in bytes
     */
    private Long size;

    /**
     * Whether the resource supports subscriptions
     */
    private Boolean subscribable = false;

    /**
     * Get text content (for backward compatibility)
     */
    public String getText() {
        return contents;
    }
} 