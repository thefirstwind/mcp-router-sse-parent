package com.nacos.mcp.server.v5.config;

import org.springframework.http.HttpStatus;

/**
 * HttpStatusCode 适配器类
 * 用于在 Spring Boot 2.7.18 中替代 Spring Framework 6.x 的 HttpStatusCode
 */
public class HttpStatusCodeAdapter {
    
    private final HttpStatus httpStatus;
    
    public HttpStatusCodeAdapter(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
    
    public HttpStatusCodeAdapter(int statusCode) {
        this.httpStatus = HttpStatus.valueOf(statusCode);
    }
    
    public int value() {
        return httpStatus.value();
    }
    
    public String name() {
        return httpStatus.name();
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
    
    public static HttpStatusCodeAdapter of(int statusCode) {
        return new HttpStatusCodeAdapter(statusCode);
    }
    
    public static HttpStatusCodeAdapter of(HttpStatus httpStatus) {
        return new HttpStatusCodeAdapter(httpStatus);
    }
    
    @Override
    public String toString() {
        return httpStatus.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HttpStatusCodeAdapter that = (HttpStatusCodeAdapter) obj;
        return httpStatus == that.httpStatus;
    }
    
    @Override
    public int hashCode() {
        return httpStatus.hashCode();
    }
} 