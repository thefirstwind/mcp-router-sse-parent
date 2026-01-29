# Nacos 老配置处理和迁移指南

## 问题背景

当我们修复了配置更新机制后，Nacos 中可能已经存在一些老的、过时的配置。这些老配置可能会导致：

1. **配置混乱**：新旧配置并存，难以管理
2. **路由错误**：mcp-router 可能读取到错误的配置版本
3. **工具定义不一致**：老配置中的工具定义可能已过时
4. **元数据冲突**：服务实例注册的元数据可能指向错误的配置文件

## 老配置识别

### 场景 1：配置文件名格式不统一

**可能存在的配置命名**：
```
# 老格式（没有 UUID）
mcp-server-v5-mcp-tools.json
mcp-server-v5-mcp-server.json
mcp-server-v5-mcp-versions.json

# 标准格式（带 UUID 和版本）
02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-tools.json
02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-server.json
02bdea21-6b44-4432-9e8e-16514ebd8cb8-mcp-versions.json
```

### 场景 2：配置内容已过时

即使文件名正确，内容也可能过时：
- 缺少新添加的工具
- 工具参数定义不完整
- 服务端点信息错误

## 处理策略

### 策略 A：覆盖式更新（当前方案）

**优点**：
- 简单直接，自动处理
- 启动时自动检测并更新

**缺点**：
- 不保留历史版本
- 无法回滚

**适用场景**：
- 单一版本部署
- 不需要版本共存
- 快速迭代开发环境

**实现**：
我们已经在 `NacosRegistrationConfigFixed.java` 中实现了这个策略：
```java
// MD5 不同时自动覆盖
if (!localMd5.equals(remoteMd5)) {
    logger.info("🔄 Config content changed, will force update");
    configService.publishConfig(dataId, group, content, ConfigType.JSON.getType());
}
```

### 策略 B：版本化管理（推荐用于生产）

**优点**：
- 支持多版本共存
- 可以回滚到历史版本
- 便于灰度发布

**缺点**：
- 需要管理配置生命周期
- Nacos 配置会增多
- 需要额外的清理机制

**适用场景**：
- 生产环境
- 需要灰度发布
- 多版本共存

**实现方案**：见下文

## 版本化配置管理实现

### 1. 配置命名规范

```
格式：{服务UUID}-{版本号}-{配置类型}.json

示例：
02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.0-mcp-tools.json
02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-tools.json
02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.2-mcp-tools.json
```

### 2. 修改注册逻辑

在 `NacosRegistrationConfig.java` 中，将 dataId 改为包含版本号：

```java
// 修改前
String toolsConfigDataId = applicationName + "-mcp-tools.json";

// 修改后
String serverUuid = getOrCreateServerUuid();  // 从配置文件读取或生成
String toolsConfigDataId = serverUuid + "-" + serverVersion + "-mcp-tools.json";
```

### 3. 版本索引配置

创建一个版本索引配置，记录所有可用版本：

```json
{
  "id": "02bdea21-6b44-4432-9e8e-16514ebd8cb8",
  "name": "mcp-server-v5",
  "latestVersion": "1.0.2",
  "versions": [
    {
      "version": "1.0.0",
      "releaseDate": "2025-01-01T00:00:00Z",
      "toolsConfig": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.0-mcp-tools.json",
      "serverConfig": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.0-mcp-server.json",
      "deprecated": true
    },
    {
      "version": "1.0.1",
      "releaseDate": "2025-01-15T00:00:00Z",
      "toolsConfig": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-tools.json",
      "serverConfig": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-server.json",
      "deprecated": false
    },
    {
      "version": "1.0.2",
      "releaseDate": "2025-01-29T00:00:00Z",
      "toolsConfig": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.2-mcp-tools.json",
      "serverConfig": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.2-mcp-server.json",
      "deprecated": false,
      "latest": true
    }
  ]
}
```

### 4. 服务实例元数据

在服务注册时，明确指定使用的配置版本：

```java
metadata.put("mcp.version", serverVersion);
metadata.put("tools.config", toolsConfigDataId);
metadata.put("tools.config.version", serverVersion);
metadata.put("server.config", serverConfigDataId);
metadata.put("server.config.version", serverVersion);
```

## 老配置清理方案

### 自动清理脚本

创建一个清理工具，定期清理过期配置：

```java
@Service
public class NacosConfigCleanupService {
    
    @Autowired
    private ConfigService configService;
    
    /**
     * 清理过期的配置
     * @param serverUuid 服务 UUID
     * @param keepVersions 保留最近几个版本（默认3个）
     */
    public void cleanupOldConfigs(String serverUuid, int keepVersions) {
        try {
            // 1. 读取版本索引
            String versionIndexDataId = serverUuid + "-mcp-versions.json";
            String versionIndexContent = configService.getConfig(versionIndexDataId, "mcp-server-versions", 5000);
            
            // 2. 解析版本列表
            JSONObject versionIndex = JSON.parseObject(versionIndexContent);
            JSONArray versions = versionIndex.getJSONArray("versions");
            
            // 3. 按日期排序，找出要删除的版本
            List<String> versionsToDelete = findVersionsToDelete(versions, keepVersions);
            
            // 4. 删除过期配置
            for (String version : versionsToDelete) {
                String toolsConfigId = serverUuid + "-" + version + "-mcp-tools.json";
                String serverConfigId = serverUuid + "-" + version + "-mcp-server.json";
                
                configService.removeConfig(toolsConfigId, "mcp-tools");
                configService.removeConfig(serverConfigId, "mcp-server");
                
                logger.info("✅ Deleted old config for version: {}", version);
            }
            
            // 5. 更新版本索引，标记已删除的版本
            updateVersionIndex(versionIndex, versionsToDelete);
            
        } catch (Exception e) {
            logger.error("❌ Failed to cleanup old configs", e);
        }
    }
}
```

### 手动清理步骤

1. **登录 Nacos 控制台**：
   ```
   http://localhost:8848/nacos
   ```

2. **进入配置管理 → 配置列表**

3. **识别老配置**：
   - 查找不符合命名规范的配置
   - 检查配置的修改时间
   - 对比配置内容的 MD5

4. **备份老配置**（可选）：
   ```bash
   # 导出配置到本地
   curl -X GET "http://localhost:8848/nacos/v1/cs/configs?dataId=xxx&group=yyy" > backup.json
   ```

5. **删除老配置**：
   - 在控制台点击删除按钮
   - 或使用 API：
   ```bash
   curl -X DELETE "http://localhost:8848/nacos/v1/cs/configs?dataId=xxx&group=yyy"
   ```

## 迁移实施步骤

### 阶段 1：现状评估

```bash
# 列出 Nacos 中所有相关配置
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?search=accurate&dataId=&group=mcp-tools&pageNo=1&pageSize=100"
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?search=accurate&dataId=&group=mcp-server&pageNo=1&pageSize=100"
```

### 阶段 2：部署新版本

1. 应用 `NacosRegistrationConfigFixed.java` 的修改
2. 重新编译并部署
3. 观察日志，确认配置更新成功

### 阶段 3：验证新配置

1. 检查 Nacos 中的配置是否已更新
2. 验证服务实例的元数据
3. 测试工具调用是否正常

### 阶段 4：清理老配置（可选）

1. 确认新配置运行稳定（建议运行1-2天）
2. 备份老配置
3. 删除或归档老配置

## 平滑升级策略

### 蓝绿部署

1. **保持老版本运行**：
   ```
   老服务：使用老配置（mcp-server-v5-1.0.0）
   新服务：使用新配置（mcp-server-v5-1.0.1）
   ```

2. **Router 逐步切流量**：
   - 先切 10% 流量到新版本
   - 观察错误率
   - 逐步增加到 100%

3. **确认无误后下线老版本**

### 灰度发布

1. **设置权重**：
   ```java
   instance.setWeight(1.0);  // 新版本
   instance.setWeight(0.1);  // 老版本（逐步降低）
   ```

2. **使用配置开关**：
   ```yaml
   mcp:
     server:
       version:
         1.0.0:
           enabled: true
           weight: 0.2
         1.0.1:
           enabled: true
           weight: 0.8
   ```

## 监控和告警

### 配置版本监控

```java
@Component
public class ConfigVersionMonitor {
    
    @Scheduled(fixedRate = 60000)  // 每分钟检查一次
    public void checkConfigConsistency() {
        // 1. 读取所有服务实例
        List<Instance> instances = namingService.getAllInstances(serviceName);
        
        // 2. 检查配置版本是否一致
        Map<String, Long> versionCount = instances.stream()
            .collect(Collectors.groupingBy(
                i -> i.getMetadata().get("tools.config.version"),
                Collectors.counting()
            ));
        
        // 3. 如果版本不一致，发出告警
        if (versionCount.size() > 1) {
            logger.warn("⚠️ Multiple config versions detected: {}", versionCount);
            // 发送告警通知...
        }
    }
}
```

## 总结

### 推荐方案

1. **开发/测试环境**：
   - 使用策略 A（覆盖式更新）
   - 简单快速，不保留历史

2. **生产环境**：
   - 使用策略 B（版本化管理）
   - 支持回滚和灰度发布
   - 定期清理老配置（保留最近 3 个版本）

### 立即行动项

1. ✅ 应用 MD5 校验修复（已完成参考实现）
2. 📋 评估当前 Nacos 中的配置状态
3. 🧹 清理明显过期的老配置
4. 📊 建立配置版本监控
5. 📝 制定配置管理规范

## 相关文件

- `/Users/shine/projects.mcp-router-sse-parent/NACOS_TOOLS_UPDATE_ISSUE_FIX.md` - 主要问题分析
- `/Users/shine/projects.mcp-router-sse-parent/NACOS_CONFIG_UPDATE_PATCH.md` - 修复补丁
- `/Users/shine/projects.mcp-router-sse-parent/mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config/NacosRegistrationConfigFixed.java` - 修复实现
