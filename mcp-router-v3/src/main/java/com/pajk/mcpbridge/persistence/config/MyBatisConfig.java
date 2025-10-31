package com.pajk.mcpbridge.persistence.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 配置类
 * 
 * 通过 persistence.enabled 配置项控制持久化功能的启用
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
@MapperScan("com.pajk.mcpbridge.persistence.mapper")
public class MyBatisConfig {
    
    /**
     * 配置 SqlSessionFactory
     * 支持下划线转驼峰等特性
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        
        // 设置 Mapper XML 文件位置
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/**/*.xml")
        );
        
        // 设置类型别名包
        sessionFactory.setTypeAliasesPackage("com.pajk.mcpbridge.persistence.entity");
        
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultFetchSize(100);
        configuration.setDefaultStatementTimeout(30);
        
        sessionFactory.setConfiguration(configuration);
        
        return sessionFactory.getObject();
    }
}

