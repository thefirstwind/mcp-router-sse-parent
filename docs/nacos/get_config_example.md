# 如何获取 Nacos MCP 配置的具体值

## 配置文件名示例

- `f457f161-5735-46b6-b901-d2fa8e03cb21-1.0.1-mcp-tools.json`
- `f457f161-5735-46b6-b901-d2fa8e03cb21-mcp-versions.json`
- `f457f161-5735-46b6-b901-d2fa8e03cb21-1.0.1-mcp-server.json`

## 方法一：使用 McpConfigService（推荐）

### 1. 获取服务器配置 (mcp-server.json)

```java
@Autowired
private McpConfigService mcpConfigService;

// 使用 UUID 和版本号
String uuid = "f457f161-5735-46b6-b901-d2fa8e03cb21";
String version = "1.0.1";

Mono<McpServerConfig> serverConfigMono = mcpConfigService.getServerConfig(uuid, version);
serverConfigMono.subscribe(config -> {
    if (config != null) {
        System.out.println("Server Config: " + config);
    } else {
        System.out.println("Config not found");
    }
});
```

### 2. 获取工具配置 (mcp-tools.json)

```java
Mono<McpToolsConfig> toolsConfigMono = mcpConfigService.getToolsConfig(uuid, version);
toolsConfigMono.subscribe(config -> {
    if (config != null) {
        System.out.println("Tools Config: " + config);
        System.out.println("Tools: " + config.getTools());
    } else {
        System.out.println("Config not found");
    }
});
```

### 3. 获取版本配置 (mcp-versions.json)

```java
Mono<McpVersionConfig> versionConfigMono = mcpConfigService.getVersionConfig(uuid);
versionConfigMono.subscribe(config -> {
    if (config != null) {
        System.out.println("Version Config: " + config);
        System.out.println("Versions: " + config.getVersions());
    } else {
        System.out.println("Config not found");
    }
});
```

## 方法二：直接使用 ConfigService

### 1. 注入 ConfigService

```java
@Autowired
private ConfigService configService;
```

### 2. 获取原始 JSON 字符串

```java
// 获取 mcp-server.json
String serverDataId = "f457f161-5735-46b6-b901-d2fa8e03cb21-1.0.1-mcp-server.json";
String serverGroup = "mcp-server";
String serverConfigJson = configService.getConfig(serverDataId, serverGroup, 3000);
System.out.println("Server Config JSON: " + serverConfigJson);

// 获取 mcp-tools.json
String toolsDataId = "f457f161-5735-46b6-b901-d2fa8e03cb21-1.0.1-mcp-tools.json";
String toolsGroup = "mcp-tools";
String toolsConfigJson = configService.getConfig(toolsDataId, toolsGroup, 3000);
System.out.println("Tools Config JSON: " + toolsConfigJson);

// 获取 mcp-versions.json
String versionsDataId = "f457f161-5735-46b6-b901-d2fa8e03cb21-mcp-versions.json";
String versionsGroup = "mcp-server-versions";
String versionsConfigJson = configService.getConfig(versionsDataId, versionsGroup, 3000);
System.out.println("Versions Config JSON: " + versionsConfigJson);
```

### 3. 解析 JSON 为对象

```java
import com.fasterxml.jackson.databind.ObjectMapper;

@Autowired
private ObjectMapper objectMapper;

// 解析服务器配置
McpServerConfig serverConfig = objectMapper.readValue(serverConfigJson, McpServerConfig.class);

// 解析工具配置
McpToolsConfig toolsConfig = objectMapper.readValue(toolsConfigJson, McpToolsConfig.class);

// 解析版本配置
McpVersionConfig versionConfig = objectMapper.readValue(versionsConfigJson, McpVersionConfig.class);
```

## 方法三：在 Controller 中提供 API 接口

```java
@RestController
@RequestMapping("/api/mcp/config")
@RequiredArgsConstructor
public class McpConfigController {
    
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    
    /**
     * 根据 dataId 和 group 获取配置（原始 JSON）
     */
    @GetMapping("/raw")
    public ResponseEntity<String> getConfigRaw(
            @RequestParam String dataId,
            @RequestParam String group) {
        try {
            String config = configService.getConfig(dataId, group, 3000);
            if (config == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    /**
     * 获取服务器配置
     */
    @GetMapping("/server")
    public Mono<McpServerConfig> getServerConfig(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "1.0.0") String version) {
        return mcpConfigService.getServerConfig(uuid, version);
    }
    
    /**
     * 获取工具配置
     */
    @GetMapping("/tools")
    public Mono<McpToolsConfig> getToolsConfig(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "1.0.0") String version) {
        return mcpConfigService.getToolsConfig(uuid, version);
    }
    
    /**
     * 获取版本配置
     */
    @GetMapping("/versions")
    public Mono<McpVersionConfig> getVersionConfig(@RequestParam String uuid) {
        return mcpConfigService.getVersionConfig(uuid);
    }
}
```

## 使用示例

### 通过 API 调用

```bash
# 获取原始 JSON
curl "http://localhost:8051/api/mcp/config/raw?dataId=f457f161-5735-46b6-b901-d2fa8e03cb21-1.0.1-mcp-server.json&group=mcp-server"

# 获取服务器配置（解析后的对象）
curl "http://localhost:8051/api/mcp/config/server?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21&version=1.0.1"

# 获取工具配置
curl "http://localhost:8051/api/mcp/config/tools?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21&version=1.0.1"

# 获取版本配置
curl "http://localhost:8051/api/mcp/config/versions?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21"
```

## 配置组常量

```java
import com.pajk.mcpbridge.core.config.McpNacosConstants;

// 使用常量
String serverGroup = McpNacosConstants.SERVER_GROUP;        // "mcp-server"
String toolsGroup = McpNacosConstants.TOOLS_GROUP;          // "mcp-tools"
String versionsGroup = McpNacosConstants.VERSIONS_GROUP;     // "mcp-server-versions"
```

## 注意事项

1. **超时时间**: `getConfig` 方法的第三个参数是超时时间（毫秒），默认使用 3000ms
2. **返回值**: `getConfig` 可能返回 `null`，需要做空值检查
3. **异常处理**: 需要捕获 `NacosException` 和其他可能的异常
4. **异步处理**: `McpConfigService` 的方法返回 `Mono`，需要使用响应式编程方式处理

## 完整示例代码

```java
@Service
@RequiredArgsConstructor
public class ConfigExampleService {
    
    private final ConfigService configService;
    private final McpConfigService mcpConfigService;
    private final ObjectMapper objectMapper;
    
    public void example() {
        String uuid = "f457f161-5735-46b6-b901-d2fa8e03cb21";
        String version = "1.0.1";
        
        // 方法1: 使用 McpConfigService（推荐）
        mcpConfigService.getServerConfig(uuid, version)
            .doOnNext(config -> {
                if (config != null) {
                    System.out.println("Server ID: " + config.getId());
                    System.out.println("Server Name: " + config.getName());
                    System.out.println("Server Version: " + config.getVersion());
                }
            })
            .doOnError(error -> System.err.println("Error: " + error.getMessage()))
            .subscribe();
        
        // 方法2: 直接使用 ConfigService
        try {
            String dataId = uuid + "-" + version + "-mcp-server.json";
            String group = "mcp-server";
            String configJson = configService.getConfig(dataId, group, 3000);
            
            if (configJson != null) {
                McpServerConfig config = objectMapper.readValue(configJson, McpServerConfig.class);
                System.out.println("Config: " + config);
            } else {
                System.out.println("Config not found");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

















