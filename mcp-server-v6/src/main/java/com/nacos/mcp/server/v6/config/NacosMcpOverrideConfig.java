package com.nacos.mcp.server.v6.config;

import com.alibaba.cloud.ai.mcp.nacos.NacosMcpProperties;
import com.alibaba.cloud.ai.mcp.nacos.service.NacosMcpOperationService;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class NacosMcpOverrideConfig {

    @Bean
    public NacosMcpOperationService nacosMcpOperationService(NacosMcpProperties nacosMcpProperties) {
        Properties nacosProperties = nacosMcpProperties.getNacosProperties();
        try {
            // 返回自定义的 OperationService，替换默认 Bean
            return new CustomNacosMcpOperationService(nacosProperties);
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }
}
