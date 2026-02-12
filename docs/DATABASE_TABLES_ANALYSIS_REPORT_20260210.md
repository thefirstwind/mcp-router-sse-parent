# MCP 系统数据库表使用情况分析报告 (2026-02-10)

本报告汇总了 `mcp-router-v3` 和 `zk-mcp-parent` (zkInfo) 模块中核心数据库表的使用状态，识别了核心有用表、低频表以及建议清理的孤岛表。

---

## 1. ✅ 核心有用表 (Useful)
这些表在业务逻辑中处于活跃状态，被代码频繁引用，是系统的核心支撑。

| 表名 | 归属模块 | 用途说明 |
| :--- | :--- | :--- |
| `mcp_servers` | mcp-router-v3 | **核心业务表**。存储 MCP 服务器实例信息，用于服务发现、健康检查和负载均衡。 |
| `health_check_records` | mcp-router-v3 | **监控记录表**。存储所有健康检查的历史记录，用于状态追踪和性能统计。 |
| `routing_logs` | mcp-router-v3 | **日志表**。记录所有请求详情（ID、耗时、状态等），用于审计和调试。 |
| `zk_project` | zkInfo | **项目管理**。定义虚拟项目或实际项目的基础信息。 |
| `zk_project_service` | zkInfo | **关联表**。管理项目与 Dubbo 服务之间的多对多映射关系。 |
| `zk_dubbo_service` | zkInfo | **服务元数据**。存储 Dubbo 服务的接口名称、分组、版本等核心元数据。 |
| `zk_dubbo_service_node` | zkInfo | **节点管理**。存储 Dubbo 服务的实际注册节点（IP/Port）信息。 |
| `zk_dubbo_service_method` | zkInfo | **方法元数据**。存储 Dubbo 服务下属的所有方法及其签名。 |
| `zk_dubbo_method_parameter`| zkInfo | **参数详情**。存储具体方法的参数类型、顺序等详细元数据。 |
| `zk_service_approval` | zkInfo | **审批流**。控制服务的上线审批状态（如：待审批、已通过）。 |
| `zk_approval_log` | zkInfo | **审批审计**。记录服务审批过程中的所有操作轨迹。 |
| `zk_interface_whitelist` | zkInfo | **安全控制**。存储接口访问白名单，保障调用安全性。 |
| `zk_virtual_project_endpoint`| zkInfo | **路由映射**。在虚拟项目场景下，提供外部访问端点到内部服务的映射。 |

---

## 2. ⚠️ 低频/归档表 (Partially Useful)
这些表并非业务逻辑必需，但在数据维护或数据冷热分离场景下有作用。

*   **`routing_logs_archive`**: 
    *   **状态**: 仅用于数据归档。
    *   **说明**: 代码中仅在 MyBatis 映射文件中通过 SQL 任务引用，用于将老旧的 `routing_logs` 数据移入此表。没有对应的 Java Service 逻辑，仅作为历史备份。

---

## 3. ❌ 孤岛表 (Islands / Unused)
这些表在数据库定义中存在，但当前的 Java 业务逻辑完全没有使用它们。

*   **`system_config`**:
    *   **状态**: **孤岛表**。
    *   **分析**: 目前系统的配置主要来源于 `application.yml` 或 **Nacos 配置中心**。数据库中的此表虽然有数据，但代码中没有任何 DAO (Mapper) 或 Service 对其进行读写。
    *   **建议**: 建议清理，或明确其作为“备用持久化配置”的意图。

---

## 💡 总结建议
1.  **清理动作**: 对于 `system_config` 和其他未出现在上述列表但在 schema 中的表（如 `load_balancer_metrics`），可考虑在下次数据库优化时一并删除。
2.  **性能优化**: 对于 `zk_dubbo_method_parameter` 和 `zk_dubbo_service_method` 这种高频关联的表，建议确保索引完备。
3.  **日志维护**: `routing_logs` 增长较快，建议定期触发 `archiveToHistory` 操作。
