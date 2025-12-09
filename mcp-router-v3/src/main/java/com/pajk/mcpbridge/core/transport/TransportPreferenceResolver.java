package com.pajk.mcpbridge.core.transport;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Resolve the preferred transport type from request headers/query params.
 */
public class TransportPreferenceResolver {

    public static final String HEADER_TRANSPORT = "X-MCP-Transport";
    public static final String QUERY_TRANSPORT = "transport";

    /**
     * Resolve transport type. Defaults to SSE until streamable implementation is ready.
     */
    public TransportType resolve(ServerRequest request) {
        // Header takes precedence
        HttpHeaders headers = request.headers().asHttpHeaders();
        String headerValue = headers.getFirst(HEADER_TRANSPORT);
        if (StringUtils.hasText(headerValue)) {
            return parse(headerValue);
        }

        // Fallback to query parameter
        String queryValue = request.queryParam(QUERY_TRANSPORT).orElse(null);
        if (StringUtils.hasText(queryValue)) {
            return parse(queryValue);
        }

        return TransportType.SSE;
    }

    private TransportType parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return TransportType.SSE;
        }
        switch (raw.trim().toLowerCase()) {
            case "streamable":
            case "stream":
                return TransportType.STREAMABLE;
            case "sse":
            default:
                return TransportType.SSE;
        }
    }
}









