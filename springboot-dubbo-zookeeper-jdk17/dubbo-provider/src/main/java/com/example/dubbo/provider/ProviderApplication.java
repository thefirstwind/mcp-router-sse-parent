package com.example.dubbo.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dubbo服务提供者启动类
 */
@SpringBootApplication
@EnableDubbo(scanBasePackages = "com.example.dubbo.provider.service")
public class ProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
        System.out.println("Dubbo Provider服务启动成功！");
    }
} 