-- MySQL dump 10.13  Distrib 5.7.24, for osx11.1 (x86_64)
--
-- Host: 127.0.0.1    Database: mcp_bridge
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `mcp_servers`
--

DROP TABLE IF EXISTS `mcp_servers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mcp_servers` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `server_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务器唯一标识 (name:ip:port)',
  `server_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务器名称',
  `server_group` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'mcp-server' COMMENT '服务组',
  `namespace_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'public' COMMENT 'Nacos命名空间',
  `host` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务器主机地址',
  `port` int NOT NULL COMMENT '服务器端口',
  `sse_endpoint` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '/sse' COMMENT 'SSE端点路径',
  `health_endpoint` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '/health' COMMENT '健康检查端点',
  `healthy` tinyint(1) NOT NULL DEFAULT '1' COMMENT '健康状态',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `weight` double NOT NULL DEFAULT '1' COMMENT '权重',
  `ephemeral` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否临时实例',
  `cluster_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'DEFAULT' COMMENT '集群名称',
  `version` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '1.0.0' COMMENT '服务版本',
  `protocol` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'mcp-sse' COMMENT '协议类型',
  `metadata` json DEFAULT NULL COMMENT '服务器元数据',
  `tags` json DEFAULT NULL COMMENT '标签',
  `total_requests` bigint DEFAULT '0' COMMENT '总请求数',
  `total_errors` bigint DEFAULT '0' COMMENT '总错误数',
  `last_request_time` datetime DEFAULT NULL COMMENT '最后请求时间',
  `last_health_check` datetime DEFAULT NULL COMMENT '最后健康检查时间',
  `registered_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间（软删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_key` (`server_key`),
  KEY `idx_server_name` (`server_name`),
  KEY `idx_server_group` (`server_group`),
  KEY `idx_namespace` (`namespace_id`),
  KEY `idx_healthy_enabled` (`healthy`,`enabled`,`deleted_at`),
  KEY `idx_host_port` (`host`,`port`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器实例表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mcp_servers`
--

LOCK TABLES `mcp_servers` WRITE;
/*!40000 ALTER TABLE `mcp_servers` DISABLE KEYS */;
INSERT INTO `mcp_servers` VALUES (1,'test-mcp-server-alignment:127.0.0.1:8999','test-mcp-server-alignment','mcp-server','public','127.0.0.1',8999,'/sse','/health',0,1,1,0,'DEFAULT','1.0.0','mcp-sse','{\"version\": \"1.0.0\", \"server.md5\": \"99914b932bd37a50b983c5e7c90ae93b\", \"description\": \"测试原子化注册功能\", \"sseEndpoint\": \"/sse\", \"tools.names\": \"test_tool_1,test_tool_2\"}',NULL,0,0,NULL,NULL,'2025-10-30 17:52:05','2025-10-30 09:52:05','2025-10-30 11:34:17',NULL),(2,'mcp-server-v2-real:127.0.0.1:8063','mcp-server-v2-real','mcp-server','public','127.0.0.1',8063,'/sse','/health',0,1,1,0,'DEFAULT','1.0.0','mcp-sse','{\"version\": \"1.0.0\", \"server.md5\": \"99914b932bd37a50b983c5e7c90ae93b\", \"description\": \"真实的mcp-server-v2服务\", \"sseEndpoint\": \"/sse\", \"tools.names\": \"getAllPersons,addPerson,deletePerson,getCityTime\", \"transport.type\": \"sse\", \"health.endpoint\": \"http://localhost:8080/actuator/health\"}',NULL,0,0,NULL,NULL,'2025-10-30 17:52:05','2025-10-30 09:52:05','2025-10-30 11:34:17',NULL),(3,'cf-server:127.0.0.1:8899','cf-server','mcp-endpoints','public','127.0.0.1',8899,'/sse','/health',1,1,1,0,'DEFAULT','1.0.0','mcp-sse','{}',NULL,0,0,NULL,NULL,'2025-10-30 17:52:05','2025-10-30 09:52:05','2025-10-30 11:34:17',NULL),(4,'mcp-server-v2-20250718:127.0.0.1:8090','mcp-server-v2-20250718','mcp-endpoints','public','127.0.0.1',8090,'/sse','/health',1,1,1,0,'DEFAULT','1.0.0','mcp-sse','{}',NULL,0,0,NULL,NULL,'2025-10-30 17:52:05','2025-10-30 09:52:05','2025-10-30 11:34:17',NULL),(5,'mcp-router-v3:127.0.0.1:8052','mcp-router-v3','mcp-server','public','127.0.0.1',8052,'/sse','/health',1,1,1,1,'DEFAULT','1.0.0','mcp-sse','{\"role\": \"router\", \"type\": \"mcp-router\", \"version\": \"v3\", \"startTime\": \"1761824057575\", \"capabilities\": \"routing,load-balancing,event-driven\", \"acceptConnections\": \"true\"}',NULL,0,0,NULL,NULL,'2025-10-30 17:52:06','2025-10-30 09:52:05','2025-10-30 11:34:18',NULL),(6,'mcp-server-v6:192.168.0.102:8071','mcp-server-v6','mcp-server','public','192.168.0.102',8071,'/sse','/health',0,1,1,1,'DEFAULT','1.0.0','mcp-sse','{\"version\": \"1.0.1\", \"protocol\": \"mcp-sse\", \"serverName\": \"mcp-server-v6\", \"sseEndpoint\": \"/sse\", \"sseMessageEndpoint\": \"/mcp/message\"}',NULL,0,0,NULL,'2025-10-30 18:50:40','2025-10-30 17:52:08','2025-10-30 09:52:08','2025-10-30 10:50:40',NULL),(8,'mcp-server-v6:192.168.0.102:8072','mcp-server-v6','mcp-server','public','192.168.0.102',8072,'/sse','/health',0,1,1,1,'DEFAULT','1.0.0','mcp-sse','{\"version\": \"1.0.1\", \"protocol\": \"mcp-sse\", \"serverName\": \"mcp-server-v6\", \"sseEndpoint\": \"/sse\", \"sseMessageEndpoint\": \"/mcp/message\"}',NULL,0,0,NULL,'2025-10-30 18:43:42','2025-10-30 17:52:12','2025-10-30 09:52:12','2025-10-30 10:43:41',NULL),(30,'mcp-server-v6:192.168.0.102:8081','mcp-server-v6','mcp-server','public','192.168.0.102',8081,'/sse','/health',0,1,1,1,'DEFAULT','1.0.0','mcp-sse','{\"version\": \"1.0.1\", \"protocol\": \"mcp-sse\", \"serverName\": \"mcp-server-v6\", \"sseEndpoint\": \"/sse\", \"sseMessageEndpoint\": \"/mcp/message\"}',NULL,0,0,NULL,'2025-10-30 18:50:40','2025-10-30 18:43:42','2025-10-30 10:43:41','2025-10-30 10:50:40',NULL);
/*!40000 ALTER TABLE `mcp_servers` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-10-30 19:48:04
