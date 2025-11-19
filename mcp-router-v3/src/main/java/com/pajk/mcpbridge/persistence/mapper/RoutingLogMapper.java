package com.pajk.mcpbridge.persistence.mapper;

import com.pajk.mcpbridge.persistence.entity.RoutingLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 路由日志 Mapper 接口
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Mapper
public interface RoutingLogMapper {
    
    /**
     * 插入单条路由日志
     * 
     * @param log 路由日志对象
     * @return 影响的行数
     */
    int insert(RoutingLog log);
    
    /**
     * 批量插入路由日志（性能优化）
     * 
     * @param logs 路由日志列表
     * @return 影响的行数
     */
    int batchInsert(@Param("logs") List<RoutingLog> logs);
    
    /**
     * 根据请求ID查询路由日志
     * 
     * @param requestId 请求ID
     * @return 路由日志对象
     */
    RoutingLog selectByRequestId(@Param("requestId") String requestId);
    
    /**
     * 根据时间范围查询路由日志
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制返回数量
     * @return 路由日志列表
     */
    List<RoutingLog> selectByTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") Integer limit
    );
    
    /**
     * 根据目标服务器查询路由日志
     * 
     * @param targetServer 目标服务器标识
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制返回数量
     * @return 路由日志列表
     */
    List<RoutingLog> selectByTargetServer(
        @Param("targetServer") String targetServer,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") Integer limit
    );
    
    /**
     * 查询失败的路由日志
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制返回数量
     * @return 失败的路由日志列表
     */
    List<RoutingLog> selectFailures(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") Integer limit
    );

    /**
     * 根据 sessionId 查询最近的路由日志
     *
     * @param sessionId 会话ID
     * @param limit     限制返回数量
     * @return 路由日志列表
     */
    List<RoutingLog> selectBySessionId(
        @Param("sessionId") String sessionId,
        @Param("limit") Integer limit
    );
    
    /**
     * 统计路由成功率
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 成功率（0-100）
     */
    Double calculateSuccessRate(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 统计路由日志数量
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日志数量
     */
    Long countByTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 删除指定时间之前的路由日志
     * 
     * @param cutoffTime 截止时间
     * @return 删除的记录数
     */
    int deleteByTimeBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * 查询 RESTful 接口请求
     * 
     * @param serviceName 服务名称（可选）
     * @param mcpMethod MCP 方法（可选，如 "tools/call", "tools/list"）
     * @param hasSessionId sessionId 是否为空（可选，true=有sessionId, false=无sessionId, null=不筛选）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制返回数量
     * @return 路由日志列表
     */
    List<RoutingLog> selectRestfulRequests(
        @Param("serviceName") String serviceName,
        @Param("mcpMethod") String mcpMethod,
        @Param("hasSessionId") Boolean hasSessionId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") Integer limit
    );
}


