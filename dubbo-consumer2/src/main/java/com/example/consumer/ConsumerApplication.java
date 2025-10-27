package com.example.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Dubbo2 Consumer 启动类 - Spring Boot 3.5.2
 * 
 * @author example
 * @version 1.0.0
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.consumer"})
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
        
        System.out.println("=================================");
        System.out.println("Dubbo2 Consumer 启动成功！");
        System.out.println("Spring Boot 版本: 3.5.2");
        System.out.println("Dubbo 版本: 2.5.3");
        System.out.println("服务端口: 18081");
        System.out.println("注册中心: localhost:2181");
        System.out.println("=================================");
    }
} 