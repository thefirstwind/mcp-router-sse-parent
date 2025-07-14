package com.nacos.mcp.server.config;

import org.springframework.context.ApplicationContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class McpServerRegistrationInvoker {

    private final ApplicationContext applicationContext;
    private McpRouterRegistrationConfig.McpServerRegistrationService registrationService;

    @Retryable(
        value = { Exception.class },
        maxAttempts = 10,
        backoff = @Backoff(delay = 5000, multiplier = 2, maxDelay = 60000)
    )
    public void initiateRegistration() {
        if (registrationService == null) {
            this.registrationService = applicationContext.getBean(McpRouterRegistrationConfig.McpServerRegistrationService.class);
        }
        registrationService.initiateRegistration();
    }
} 