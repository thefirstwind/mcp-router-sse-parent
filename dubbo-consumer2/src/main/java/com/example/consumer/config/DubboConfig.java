package com.example.consumer.config;

import com.example.api.UserService;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dubbo 配置类
 * 使用 ReferenceBean 方式注册服务
 */
@Configuration
public class DubboConfig {

    @Bean
    public ApplicationConfig applicationConfig() {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("dubbo-consumer");
        return application;
    }

    @Bean
    public RegistryConfig registryConfig() {
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("zookeeper://127.0.0.1:2181");
        return registry;
    }

    @Bean
    public ReferenceConfig<UserService> userServiceReference(ApplicationConfig applicationConfig, RegistryConfig registryConfig) {
        ReferenceConfig<UserService> reference = new ReferenceConfig<>();
        reference.setApplication(applicationConfig);
        reference.setRegistry(registryConfig);
        reference.setInterface(UserService.class);
        reference.setVersion("1.0.0");
        reference.setGroup("user");  // 添加分组配置，匹配Provider
        reference.setTimeout(10000);
        reference.setRetries(2);
        reference.setCheck(false);
        return reference;
    }

    @Bean
    public UserService userService(ReferenceConfig<UserService> userServiceReference) {
        return userServiceReference.get();
    }
} 