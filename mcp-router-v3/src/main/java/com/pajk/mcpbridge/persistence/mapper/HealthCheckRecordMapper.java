package com.pajk.mcpbridge.persistence.mapper;

import com.pajk.mcpbridge.persistence.entity.HealthCheckRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康检查记录 Mapper 接口
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Mapper
public interface HealthCheckRecordMapper {
    
    /**
     * 插入单条健康检查记录
     */
    int insert(HealthCheckRecord record);
    
    /**
     * 批量插入健康检查记录
     */
    int batchInsert(@Param("records") List<HealthCheckRecord> records);
    
    /**
     * 根据服务器标识查询最近的健康检查记录
     */
    List<HealthCheckRecord> selectByServerKey(
        @Param("serverKey") String serverKey,
        @Param("limit") Integer limit
    );
    
    /**
     * 查询失败的健康检查记录
     */
    List<HealthCheckRecord> selectFailures(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") Integer limit
    );
    
    /**
     * 根据时间范围查询健康检查记录
     */
    List<HealthCheckRecord> selectByTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") Integer limit
    );
    
    /**
     * 统计服务器健康率
     */
    Double calculateHealthRate(
        @Param("serverKey") String serverKey,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 统计健康检查记录数量
     */
    Long countByTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}


