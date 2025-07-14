package com.nacos.mcp.router.service;

import com.nacos.mcp.router.service.impl.McpServerServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Type-safe MCP Tool Service with Spring AI integration
 * Provides function calling capabilities with strict type validation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class McpToolService {

    private final McpServerService mcpServerService;

    // ==================== TYPE-SAFE TOOL DEFINITIONS ====================

    /**
     * Type-safe echo tool with validation
     */
    public static class EchoRequest {
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 1000, message = "Message cannot exceed 1000 characters")
        private String message;
        
        @Min(value = 1, message = "Repeat count must be at least 1")
        @Max(value = 10, message = "Repeat count cannot exceed 10")
        private Integer repeat = 1;

        // Constructors
        public EchoRequest() {}
        
        public EchoRequest(String message, Integer repeat) {
            this.message = message;
            this.repeat = repeat;
        }

        // Getters and Setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Integer getRepeat() { return repeat; }
        public void setRepeat(Integer repeat) { this.repeat = repeat; }
    }

    public static class EchoResponse {
        private final String originalMessage;
        private final String echoedMessage;
        private final Integer repeatCount;
        private final Long timestamp;

        public EchoResponse(String originalMessage, String echoedMessage, Integer repeatCount) {
            this.originalMessage = originalMessage;
            this.echoedMessage = echoedMessage;
            this.repeatCount = repeatCount;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getOriginalMessage() { return originalMessage; }
        public String getEchoedMessage() { return echoedMessage; }
        public Integer getRepeatCount() { return repeatCount; }
        public Long getTimestamp() { return timestamp; }
    }

    /**
     * Type-safe calculator tool with validation
     */
    public static class CalculatorRequest {
        @NotNull(message = "First number is required")
        @DecimalMin(value = "-1000000", message = "Number too small")
        @DecimalMax(value = "1000000", message = "Number too large")
        private Double a;

        @NotNull(message = "Second number is required")
        @DecimalMin(value = "-1000000", message = "Number too small")
        @DecimalMax(value = "1000000", message = "Number too large")
        private Double b;

        @NotBlank(message = "Operation is required")
        @Pattern(regexp = "^(add|subtract|multiply|divide)$", 
                message = "Operation must be one of: add, subtract, multiply, divide")
        private String operation;

        // Constructors
        public CalculatorRequest() {}
        
        public CalculatorRequest(Double a, Double b, String operation) {
            this.a = a;
            this.b = b;
            this.operation = operation;
        }

        // Getters and Setters
        public Double getA() { return a; }
        public void setA(Double a) { this.a = a; }
        public Double getB() { return b; }
        public void setB(Double b) { this.b = b; }
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
    }

    public static class CalculatorResponse {
        private final Double result;
        private final String operation;
        private final Double operandA;
        private final Double operandB;
        private final String expression;
        private final Long timestamp;

        public CalculatorResponse(Double result, String operation, Double operandA, Double operandB) {
            this.result = result;
            this.operation = operation;
            this.operandA = operandA;
            this.operandB = operandB;
            this.expression = String.format("%.2f %s %.2f = %.2f", operandA, 
                getOperatorSymbol(operation), operandB, result);
            this.timestamp = System.currentTimeMillis();
        }

        private static String getOperatorSymbol(String operation) {
            return switch (operation.toLowerCase()) {
                case "add" -> "+";
                case "subtract" -> "-";
                case "multiply" -> "*";
                case "divide" -> "÷";
                default -> operation;
            };
        }

        // Getters
        public Double getResult() { return result; }
        public String getOperation() { return operation; }
        public Double getOperandA() { return operandA; }
        public Double getOperandB() { return operandB; }
        public String getExpression() { return expression; }
        public Long getTimestamp() { return timestamp; }
    }

    // ==================== TYPE-SAFE TOOL IMPLEMENTATIONS ====================

    /**
     * Type-safe echo tool implementation
     */
    public EchoResponse echo(EchoRequest request) {
        log.info("Echo tool called with message: '{}', repeat: {}", 
                request.getMessage(), request.getRepeat());
        
        validateRequest(request);
        
        String echoedMessage = String.join(" ", 
                Collections.nCopies(request.getRepeat(), request.getMessage()));
        
        return new EchoResponse(request.getMessage(), echoedMessage, request.getRepeat());
    }

    /**
     * Type-safe calculator tool implementation
     */
    public CalculatorResponse calculator(CalculatorRequest request) {
        log.info("Calculator tool called: {} {} {}", 
                request.getA(), request.getOperation(), request.getB());
        
        validateRequest(request);
        
        double result = switch (request.getOperation().toLowerCase()) {
            case "add" -> request.getA() + request.getB();
            case "subtract" -> request.getA() - request.getB();
            case "multiply" -> request.getA() * request.getB();
            case "divide" -> {
                if (request.getB() == 0.0) {
                    throw new IllegalArgumentException("Division by zero is not allowed");
                }
                yield request.getA() / request.getB();
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + request.getOperation());
        };
        
        return new CalculatorResponse(result, request.getOperation(), request.getA(), request.getB());
    }

    // ==================== VALIDATION HELPERS ====================

    private void validateRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
    }

    // ==================== FUNCTION CALLBACK REGISTRATION ====================

    /**
     * Get type-safe function definitions for Spring AI integration
     * Note: Spring AI function callbacks will be configured separately in the configuration layer
     */
    public Map<String, Class<?>> getToolDefinitions() {
        Map<String, Class<?>> definitions = new HashMap<>();
        definitions.put("echo", EchoRequest.class);
        definitions.put("calculator", CalculatorRequest.class);
        return definitions;
    }
    
    /**
     * Get tool descriptions for MCP protocol
     */
    public Map<String, String> getToolDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("echo", "Echo back a message with optional repetition");
        descriptions.put("calculator", "Perform basic mathematical operations (add, subtract, multiply, divide)");
        return descriptions;
    }

    // ==================== LEGACY SUPPORT METHODS ====================

    /**
     * Legacy method support for backward compatibility
     */
    public String echo(String message) {
        EchoRequest request = new EchoRequest(message, 1);
        EchoResponse response = echo(request);
        return response.getEchoedMessage();
    }

    public double calculator(double a, double b, String operation) {
        CalculatorRequest request = new CalculatorRequest(a, b, operation);
        CalculatorResponse response = calculator(request);
        return response.getResult();
    }

    public Map<String, Object> searchMcpServers(String query, Integer limit) {
        log.info("Search MCP servers called with query: {}, limit: {}", query, limit);
        
        int maxResults = limit != null ? limit : 10;
        
        // TODO: In a real implementation, this would search via SSE protocol and Nacos registry
        log.warn("MCP server search not yet implemented for SSE protocol - returning empty list");
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("limit", maxResults);
        response.put("results", new ArrayList<>());
        response.put("count", 0);
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    public Map<String, Object> listMcpServers() {
        log.info("List MCP servers tool called");
        
        try {
            var serversReactive = mcpServerService.listAllMcpServers();
            var servers = serversReactive.block();
            
            List<Map<String, Object>> serverList = servers.stream()
                .map(server -> {
                    Map<String, Object> serverMap = new HashMap<>();
                    serverMap.put("name", server.getName());
                    serverMap.put("status", server.getStatus().toString());
                    serverMap.put("transport", server.getTransportType() != null ? server.getTransportType() : "unknown");
                    serverMap.put("version", server.getVersion() != null ? server.getVersion() : "1.0.0");
                    serverMap.put("description", server.getDescription() != null ? server.getDescription() : "");
                    serverMap.put("registrationTime", server.getRegistrationTime() != null ? server.getRegistrationTime().toString() : "");
                    serverMap.put("toolCount", server.getTools() != null ? server.getTools().size() : 0);
                    return serverMap;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("servers", serverList);
            response.put("count", serverList.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return response;
        } catch (Exception e) {
            log.error("Failed to list MCP servers", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to list MCP servers: " + e.getMessage());
            errorResponse.put("servers", new ArrayList<>());
            errorResponse.put("count", 0);
            errorResponse.put("timestamp", System.currentTimeMillis());
            return errorResponse;
        }
    }

    public Map<String, Object> getMcpServerInfo(String serverName) {
        log.info("Get MCP server info called for: {}", serverName);
        
        try {
            var serverReactive = mcpServerService.getMcpServer(serverName);
            var server = serverReactive.block();
            
            if (server == null) {
                Map<String, Object> notFoundResponse = new HashMap<>();
                notFoundResponse.put("error", "Server not found: " + serverName);
                notFoundResponse.put("serverName", serverName);
                notFoundResponse.put("timestamp", System.currentTimeMillis());
                return notFoundResponse;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("name", server.getName());
            response.put("status", server.getStatus().toString());
            response.put("transport", server.getTransportType() != null ? server.getTransportType() : "unknown");
            response.put("version", server.getVersion() != null ? server.getVersion() : "1.0.0");
            response.put("description", server.getDescription() != null ? server.getDescription() : "");
            response.put("endpoint", server.getEndpoint() != null ? server.getEndpoint() : "");
            response.put("installCommand", server.getInstallCommand() != null ? server.getInstallCommand() : "");
            response.put("registrationTime", server.getRegistrationTime() != null ? server.getRegistrationTime().toString() : "");
            response.put("lastUpdateTime", server.getLastUpdateTime() != null ? server.getLastUpdateTime().toString() : "");
            
            if (server.getTools() != null) {
                List<Map<String, Object>> toolsList = server.getTools().stream()
                    .map(tool -> {
                        Map<String, Object> toolMap = new HashMap<>();
                        toolMap.put("name", tool.getName());
                        toolMap.put("description", tool.getDescription());
                        return toolMap;
                    })
                    .collect(Collectors.toList());
                response.put("tools", toolsList);
            } else {
                response.put("tools", new ArrayList<>());
            }
            
            response.put("timestamp", System.currentTimeMillis());
            
            return response;
        } catch (Exception e) {
            log.error("Failed to get MCP server info for: {}", serverName, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get server info: " + e.getMessage());
            errorResponse.put("serverName", serverName);
            errorResponse.put("timestamp", System.currentTimeMillis());
            return errorResponse;
        }
    }

    public Map<String, Object> executeMcpTool(String serverName, String toolName, String parameters) {
        log.info("Execute MCP tool called: server={}, tool={}, params={}", serverName, toolName, parameters);
        
        try {
            Map<String, Object> params = parameters != null && !parameters.trim().isEmpty() 
                ? Map.of("data", parameters) 
                : new HashMap<>();
            
            var resultReactive = mcpServerService.useTool(serverName, toolName, params);
            var result = resultReactive.block();
            
            Map<String, Object> response = new HashMap<>();
            response.put("serverName", serverName);
            response.put("toolName", toolName);
            response.put("parameters", parameters);
            response.put("result", result);
            response.put("status", "success");
            response.put("timestamp", System.currentTimeMillis());
            
            return response;
        } catch (Exception e) {
            log.error("Failed to execute MCP tool: server={}, tool={}", serverName, toolName, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("serverName", serverName);
            errorResponse.put("toolName", toolName);
            errorResponse.put("parameters", parameters);
            errorResponse.put("error", "Tool execution failed: " + e.getMessage());
            errorResponse.put("status", "error");
            errorResponse.put("timestamp", System.currentTimeMillis());
            return errorResponse;
        }
    }
} 