package com.pajk.mcpbridge.core.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebFlux 配置类
 * 配置响应式Web相关组件
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Bean
    public WebClient webClient() {
        // 配置 HttpClient 的超时设置
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60)) // 响应超时60秒
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000); // 连接超时30秒
        
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(1024 * 1024)) // 1MB buffer
                .build();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}