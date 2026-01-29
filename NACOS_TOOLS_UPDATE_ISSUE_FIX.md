# Nacos MCP Tools 配置不更新问题的解决方案

## 问题描述

当 MCP Server (如 mcp-server-v6) 升级接口时，比如某个工具添加了字段或修改了参数，重新部署后，工具信息不会在 Nacos 上自动更新。

## 根本原因

1. **配置发布不是强制更新**：
   - `ConfigService.publishConfig()` 方法在默认情况下，如果 Nacos 已存在相同 dataId 的配置，可能不会强制覆盖
   
2. **缺少版本控制**：
   - dataId 使用固定格式（如 `mcp-server-v6-mcp-tools.json`），没有包含版本号
   - 重新部署时使用相同的 dataId，Nacos 无法识别这是一个新版本

3. **没有配置变更检测**：
   - 注册逻辑没有比较本地配置和 Nacos 上的配置差异
   - 即使内容发生变化，也不会触发强制更新

## 解决方案

### 方案 1：强制更新 + MD5 校验（推荐）

在上传配置到 Nacos 之前，先检查远程配置是否存在以及内容是否相同。如果不同，则强制更新。

**优点**：
- 简单直接，不改变现有的 dataId 结构
- 只在配置真正变化时才更新，避免不必要的操作
- MD5 校验确保内容一致性

**缺点**：
- 每次启动都需要读取远程配置进行比较
- 如果 Nacos 出现问题，可能影响启动速度

**实现代码**：见下文的修改方案

### 方案 2：基于版本的 DataId（更健壮）

在 dataId 中包含版本号，每次版本升级都创建新的配置文件。

**优点**：
- 支持多版本共存
- 可以保留历史版本配置
- 更符合微服务最佳实践

**缺点**：
- 需要修改更多代码
- 需要管理旧版本配置的清理
- Nacos 配置文件会增多

**实现建议**：
- dataId 格式：`{md5}-{version}-mcp-tools.json`（已在 nacos-register.md 中体现）
- 在服务注册时，metadata 中包含 version 信息
- Router 可以根据版本号选择对应的配置

## 修改方案（方案 1 的实现）

### 1. 修改 `NacosRegistrationConfig.java`

在 `uploadConfigToNacos` 方法中添加强制更新逻辑：

```java
/**
 * 上传配置到Nacos（带MD5校验和强制更新）
 */
private void uploadConfigToNacos(String dataId, String group, String content) {
    try {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("namespace", namespace);

        ConfigService configService = NacosFactory.createConfigService(properties);
        
        // 1. 先检查配置是否已存在
        String existingConfig = null;
        try {
            existingConfig = configService.getConfig(dataId, group, 5000);
        } catch (NacosException e) {
            logger.warn("Failed to get existing config from Nacos: {}, will create new one", dataId);
        }
        
        // 2. 计算本地配置和远程配置的 MD5
        String localMd5 = calculateMd5(content);
        boolean needUpdate = false;
        
        if (existingConfig == null || existingConfig.isEmpty()) {
            logger.info("Config does not exist in Nacos, will create: {}", dataId);
            needUpdate = true;
        } else {
            String remoteMd5 = calculateMd5(existingConfig);
            if (!localMd5.equals(remoteMd5)) {
                logger.info("Config content changed (local MD5: {}, remote MD5: {}), will update: {}", 
                    localMd5, remoteMd5, dataId);
                needUpdate = true;
            } else {
                logger.info("Config content unchanged, skip update: {}", dataId);
            }
        }
        
        // 3. 如果需要更新，则发布配置
        if (needUpdate) {
            boolean result = configService.publishConfig(dataId, group, content, ConfigType.JSON.getType());
            if (result) {
                logger.info("✅ Successfully published config to Nacos: {}, group: {}, MD5: {}", 
                    dataId, group, localMd5);
            } else {
                logger.warn("❌ Failed to publish config to Nacos: {}, group: {}", dataId, group);
            }
        }
        
    } catch (NacosException e) {
        logger.error("❌ Error publishing config to Nacos: {}", e.getMessage(), e);
    }
}
```

### 2. 添加配置版本元数据

在 `registerToNacos` 方法中，为服务实例添加配置版本信息：

```java
// 在 metadata 中添加配置的 MD5 信息
String toolsMd5 = calculateMd5(toolsConfigContent);
metadata.put("tools.md5", toolsMd5);
metadata.put("tools.config", toolsConfigDataId);
metadata.put("server.config", serverConfigDataId);
```

### 3. 添加手动更新接口（可选）

为了支持运行时更新配置，可以添加一个管理接口：

```java
@RestController
@RequestMapping("/admin")
public class AdminController {
    
    @Autowired
    private NacosRegistrationConfig nacosRegistrationConfig;
    
    /**
     * 手动刷新工具配置到 Nacos
     */
    @PostMapping("/refresh-tools-config")
    public Map<String, Object> refreshToolsConfig() {
        try {
            nacosRegistrationConfig.registerToNacos();
            return Map.of("success", true, "message", "Tools configuration refreshed successfully");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
```

## 验证步骤

1. **修改工具定义**：
   - 在某个 @Tool 方法中添加新参数或修改描述

2. **重新部署服务**：
   ```bash
   mvn clean package
   java -jar target/mcp-server-v6.jar
   ```

3. **检查日志**：
   - 查看是否输出 "Config content changed" 日志
   - 确认 MD5 值不同
   - 验证配置已成功发布

4. **验证 Nacos 配置**：
   - 登录 Nacos 控制台
   - 检查 `mcp-tools` 组下的配置文件
   - 确认内容已更新

5. **测试工具调用**：
   - 通过 mcp-router-v3 调用更新后的工具
   - 验证新字段是否生效

## 最佳实践建议

1. **使用版本化的 DataId**（方案2）：
   - 格式：`{uuid}-{version}-mcp-tools.json`
   - 每次版本升级创建新配置
   - 在服务实例 metadata 中标记使用的配置版本

2. **添加配置变更监听**：
   - 在 Router 端监听配置变更
   - 当工具配置更新时，自动刷新本地缓存

3. **配置发布通知**：
   - 发布配置后，主动通知 Router 刷新
   - 可以通过 HTTP 回调或消息队列实现

4. **健康检查集成**：
   - 在健康检查中包含配置版本信息
   - 帮助快速定位配置不一致问题

## 相关文件

- `/Users/shine/projects.mcp-router-sse-parent/mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config/NacosRegistrationConfig.java`
- `/Users/shine/projects.mcp-router-sse-parent/mcp-server-v6/nacos-register.md`
- `/Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/registry/McpServerRegistry.java`
