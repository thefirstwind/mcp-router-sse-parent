package com.pajk.mcpbridge.persistence.mapper;

import com.pajk.mcpbridge.persistence.entity.McpServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP服务器注册信息持久化Mapper
 * 用于记录和查询服务器的注册、注销和状态变更
 */
@Mapper
public interface McpServerMapper {
    
    /**
     * 插入或更新服务器注册信息 (基于serverKey唯一约束)
     * 如果serverKey已存在，则更新；否则插入
     */
    int insertOrUpdate(McpServer server);
    
    /**
     * 根据serverKey查询服务器信息
     */
    McpServer selectByServerKey(@Param("serverKey") String serverKey);
    
    /**
     * 根据服务名称和服务分组查询所有服务器
     */
    List<McpServer> selectByServiceNameAndGroup(
        @Param("serverName") String serverName, 
        @Param("serviceGroup") String serviceGroup
    );
    
    /**
     * 查询所有在线服务器（未软删除）
     */
    List<McpServer> selectAllOnlineServers();
    
    /**
     * 查询所有健康的服务器
     */
    List<McpServer> selectAllHealthyServers();
    
    /**
     * 更新服务器健康状态
     */
    int updateHealthStatus(
        @Param("serverKey") String serverKey,
        @Param("healthy") Boolean healthy,
        @Param("lastHealthCheck") LocalDateTime lastHealthCheck
    );
    
    /**
     * 更新健康检查时间
     */
    int updateHealthCheck(
        @Param("serverKey") String serverKey,
        @Param("lastHealthCheck") LocalDateTime lastHealthCheck
    );
    
    /**
     * 标记服务器为离线状态（软删除）
     */
    int markOffline(
        @Param("serverKey") String serverKey,
        @Param("deletedAt") LocalDateTime deletedAt
    );
    
    /**
     * 批量标记服务器为离线（用于清理过期服务器）
     */
    int batchMarkOffline(
        @Param("serverKeys") List<String> serverKeys,
        @Param("deletedAt") LocalDateTime deletedAt
    );
    
    /**
     * 根据健康检查超时时间查询可能已离线的服务器
     */
    List<McpServer> selectServersByHealthCheckTimeout(
        @Param("timeoutMinutes") int timeoutMinutes
    );
    
    /**
     * 永久删除指定时间之前的离线服务器记录
     */
    int deleteOfflineServersBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 统计在线服务器数量
     */
    int countOnlineServers();
    
    /**
     * 统计健康服务器数量
     */
    int countHealthyServers();
    
    /**
     * 标记指定服务名的所有临时节点为不健康
     * 用于处理临时节点完全下线的情况
     */
    int markEphemeralInstancesUnhealthyByService(
        @Param("serverName") String serverName,
        @Param("lastHealthCheck") LocalDateTime lastHealthCheck
    );
    
    /**
     * 标记过期的临时节点为不健康
     * 查找所有 healthy=1, ephemeral=1 且 updated_at 超过指定分钟数的记录
     */
    int markStaleEphemeralInstancesUnhealthy(
        @Param("staleMinutes") int staleMinutes,
        @Param("lastHealthCheck") LocalDateTime lastHealthCheck
    );
}
