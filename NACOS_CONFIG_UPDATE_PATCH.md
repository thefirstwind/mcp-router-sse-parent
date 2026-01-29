# Nacos 配置更新问题修复补丁

## 修改文件
`/Users/shine/projects.mcp-router-sse-parent/mcp-server-v5/src/main/java/com/nacos/mcp/server/v5/config/NacosRegistrationConfig.java`

## 修改说明

需要修改 `uploadConfigToNacos` 方法（第255-275行），将原来的简单发布逻辑改为带 MD5 校验的智能更新逻辑。

### 原代码（第255-275行）

```java
    /**
     * 上传配置到Nacos
     */
    private void uploadConfigToNacos(String dataId, String group, String content) {
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            properties.put("namespace", namespace);

            ConfigService configService = NacosFactory.createConfigService(properties);
            boolean result = configService.publishConfig(dataId, group, content, ConfigType.JSON.getType());

            if (result) {
                logger.info("Successfully published config to Nacos: {}, group: {}", dataId, group);
            } else {
                logger.warn("Failed to publish config to Nacos: {}, group: {}", dataId, group);
            }
        } catch (NacosException e) {
            logger.error("Error publishing config to Nacos: {}", e.getMessage(), e);
        }
    }
```

### 新代码（替换为）

```java
    /**
     * 上传配置到Nacos（带MD5校验和强制更新）
     * 
     * 修复说明：
     * 1. 先从 Nacos 读取现有配置
     * 2. 比较本地配置和远程配置的 MD5
     * 3. 只在 MD5 不同时才更新配置
     * 4. 这样可以确保接口升级时配置一定会被更新
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
                logger.warn("⚠️ Failed to get existing config from Nacos: {}, will create new one", dataId);
            }
            
            // 2. 计算本地配置和远程配置的 MD5
            String localMd5 = calculateMd5(content);
            boolean needUpdate = false;
            
            if (existingConfig == null || existingConfig.isEmpty()) {
                logger.info("📝 Config does not exist in Nacos, will create: {}", dataId);
                needUpdate = true;
            } else {
                String remoteMd5 = calculateMd5(existingConfig);
                if (!localMd5.equals(remoteMd5)) {
                    logger.info("🔄 Config content changed (local MD5: {}, remote MD5: {}), will force update: {}", 
                        localMd5, remoteMd5, dataId);
                    needUpdate = true;
                } else {
                    logger.info("✓ Config content unchanged (MD5: {}), skip update: {}", localMd5, dataId);
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

## 额外修改（可选但推荐）

在 `registerToNacos` 方法的元数据设置部分（约第114行后），添加配置 MD5 追踪：

```java
// 在第91行附近添加
String toolsMd5 = calculateMd5(toolsConfigContent);

// 在第115行后添加（metadata 设置区域）
metadata.put("tools.md5", toolsMd5);
metadata.put("tools.config", toolsConfigDataId);
metadata.put("server.config", serverConfigDataId);
```

## 应用到 mcp-server-v6

同样的修改也需要应用到 mcp-server-v6 项目（如果它有类似的注册逻辑）。

## 测试方法

1. 修改代码后重新编译：
```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-server-v5
mvn clean package
```

2. 启动服务，查看日志中的 MD5 信息：
```bash
java -jar target/mcp-server-v5-*.jar
```

3. 修改某个工具的描述或参数

4. 重新编译并启动，应该看到类似日志：
```
🔄 Config content changed (local MD5: abc123, remote MD5: def456), will force update: mcp-server-v5-mcp-tools.json
✅ Successfully published config to Nacos: mcp-server-v5-mcp-tools.json, group: mcp-tools, MD5: abc123
```

5. 如果没有修改，应该看到：
```
✓ Config content unchanged (MD5: abc123), skip update: mcp-server-v5-mcp-tools.json
```
