package com.example.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Dubbo2 Provider 启动类
 * 
 * @author example
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDubbo
@ComponentScan(basePackages = {"com.example.provider"})
public class ProviderApplication {

    public static void main(String[] args) {
        System.setProperty("dubbo.application.logger", "slf4j");
        System.setProperty("dubbo.log.level", "INFO");
        
        SpringApplication.run(ProviderApplication.class, args);
        
        System.out.println("=================================");
        System.out.println("Dubbo2 Provider 启动成功！");
        System.out.println("服务端口: 20880");
        System.out.println("注册中心: localhost:2181");
        System.out.println("=================================");
    }
} 