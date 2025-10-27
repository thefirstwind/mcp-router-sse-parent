package com.pajk.mcpbridge.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP工具配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolsConfig {
    
    /**
     * 工具列表（结构与官方一致）
     */
    private List<McpTool> tools;
    
    /**
     * 工具元数据（必须存在，允许为空对象）
     */
    @Builder.Default
    private Map<String, Object> toolsMeta = new java.util.HashMap<>();
    
    /**
     * 工具信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpTool {
        
        /**
         * 工具名称
         */
        private String name;
        
        /**
         * 工具描述
         */
        private String description;
        
        /**
         * 输入Schema
         */
        private InputSchema inputSchema;
        
        /**
         * 输入Schema定义
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class InputSchema {
            
            /**
             * 类型
             */
            private String type;
            
            /**
             * 属性
             */
            private Map<String, Property> properties;
            
            /**
             * 必需的字段
             */
            private List<String> required;
            
            /**
             * 是否允许额外属性
             */
            private boolean additionalProperties;
            
            /**
             * 属性定义
             */
            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Property {
                
                /**
                 * 类型
                 */
                private String type;
                
                /**
                 * 格式
                 */
                private String format;
                
                /**
                 * 描述
                 */
                private String description;
                
                /**
                 * 枚举值
                 */
                private List<String> enumValues;
                
                /**
                 * 默认值
                 */
                private Object defaultValue;
            }
        }
    }
} 