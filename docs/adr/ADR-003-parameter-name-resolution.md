# 参数名解析架构设计

> **需求**: REQ-20260211-002  
> **目标**: 从GitLab/GitHub源代码获取Dubbo接口的真实参数名  
> **创建时间**: 2026-02-11 19:10

---

## 📋 目录

1. [架构概览](#架构概览)
2. [核心组件](#核心组件)
3. [实现细节](#实现细节)
4. [数据流](#数据流)
5. [配置示例](#配置示例)
6. [API设计](#api设计)

---

## 架构概览

### 三级降级策略

```
虚拟节点创建流程
    ↓
┌─────────────────────────────────────┐
│ 1️⃣ 优先：源代码分析 (GitLab/GitHub) │ → 最可靠、最完整
└─────────────────────────────────────┘
    ↓ 失败（网络/配置问题）
┌─────────────────────────────────────┐
│ 2️⃣ 降级：Class文件MethodParameters   │ → 需要-parameters编译
└─────────────────────────────────────┘
    ↓ 失败（未启用-parameters）
┌─────────────────────────────────────┐
│ 3️⃣ 兜底：默认参数名 arg0, arg1...    │ → 保证功能可用
└─────────────────────────────────────┘
```

### 系统架构图

```
┌────────────────────────────────────────────────────────────┐
│                    zkInfo 服务                              │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │         JarScannerService (JAR扫描入口)             │  │
│  └─────────────────┬───────────────────────────────────┘  │
│                    │                                       │
│                    ↓                                       │
│  ┌─────────────────────────────────────────────────────┐  │
│  │       ParameterNameResolver (参数名解析协调器)      │  │
│  │  ┌──────────────────────────────────────────────┐   │  │
│  │  │  getParameterNames(interface, method)        │   │  │
│  │  │  • 实现三级降级策略                           │   │  │
│  │  │  • 管理缓存                                   │   │  │
│  │  │  • 日志记录                                   │   │  │
│  │  └──────────────────────────────────────────────┘   │  │
│  └─────────┬────────────┬─────────────┬────────────────┘  │
│            │            │             │                   │
│            ↓            ↓             ↓                   │
│  ┌──────────────┐ ┌─────────┐ ┌───────────────┐          │
│  │SourceCode    │ │Class    │ │Default        │          │
│  │Analyzer      │ │File     │ │Parameter      │          │
│  │Service       │ │Analyzer │ │Generator      │          │
│  └──────┬───────┘ └─────────┘ └───────────────┘          │
│         │                                                 │
│         ↓                                                 │
│  ┌──────────────┐                                         │
│  │GitApiClient  │                                         │
│  │• GitLab API  │                                         │
│  │• GitHub API  │                                         │
│  └──────────────┘                                         │
└────────────────────────────────────────────────────────────┘
         │
         ↓
┌────────────────────────────────────┐
│   GitLab / GitHub                  │
│   • REST API                       │
│   • 源代码文件                      │
└────────────────────────────────────┘
```

---

## 核心组件

### 1. ParameterNameResolver （协调器）

**职责**: 统一的参数名解析入口，实现三级降级策略

**接口**:
```java
public interface ParameterNameResolver {
    /**
     * 获取方法参数名列表
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @param parameterTypes 参数类型列表
     * @return 参数名列表
     */
    List<String> getParameterNames(
        String interfaceName, 
        String methodName, 
        List<String> parameterTypes
    );
}
```

**实现**:
```java
@Service
@Slf4j
public class ParameterNameResolverImpl implements ParameterNameResolver {
    
    @Autowired
    private SourceCodeAnalyzerService sourceCodeAnalyzer;
    
    @Autowired
    private ClassFileAnalyzer classFileAnalyzer;
    
    private final Cache<String, List<String>> cache = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(1000)
        .build();
    
    @Override
    public List<String> getParameterNames(String interfaceName, String methodName, List<String> parameterTypes) {
        String cacheKey = buildCacheKey(interfaceName, methodName);
        
        // 检查缓存
        List<String> cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("✅ 从缓存获取参数名: {}.{}", interfaceName, methodName);
            return cached;
        }
        
        // 1️⃣ 尝试从源代码获取
        try {
            List<String> names = sourceCodeAnalyzer.getParameterNames(interfaceName, methodName);
            if (names != null && !names.isEmpty()) {
                log.info("✅ 从源代码获取参数名: {}.{} → {}", 
                    interfaceName, methodName, names);
                cache.put(cacheKey, names);
                return names;
            }
        } catch (Exception e) {
            log.debug("无法从源代码获取参数名: {}", e.getMessage());
        }
        
        // 2️⃣ 尝试from class文件获取
        try {
            List<String> names = classFileAnalyzer.getParameterNames(interfaceName, methodName);
            if (names != null && !names.isEmpty()) {
                log.info("⚡ 从class文件获取参数名: {}.{} → {}", 
                    interfaceName, methodName, names);
                cache.put(cacheKey, names);
                return names;
            }
        } catch (Exception e) {
            log.debug("无法从class文件获取参数名: {}", e.getMessage());
        }
        
        // 3️⃣ 兜底：生成默认参数名
        List<String> defaultNames = generateDefaultNames(parameterTypes.size());
        log.warn("⚠️ 使用默认参数名: {}.{} → {}", 
            interfaceName, methodName, defaultNames);
        return defaultNames;
    }
    
    private List<String> generateDefaultNames(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> "arg" + i)
            .collect(Collectors.toList());
    }
}
```

---

### 2. SourceCodeAnalyzerService （源代码分析）

**职责**: 从Git仓库获取源文件并解析参数名

**实现**:
```java
@Service
@Slf4j
public class SourceCodeAnalyzerService {
    
    @Autowired
    private GitApiClient gitApiClient;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    /**
     * 从源代码获取参数名
     */
    public List<String> getParameterNames(String interfaceName, String methodName) {
        // 1. 获取项目配置
        Project project = findProjectByInterface(interfaceName);
        if (project == null || StringUtils.isEmpty(project.getGitRepoUrl())) {
            log.debug("项目未配置Git仓库: {}", interfaceName);
            return null;
        }
        
        // 2. 构造源文件路径
        String sourceFilePath = interfaceNameToFilePath(interfaceName);
        // com.pajk.provider3.service.UserService 
        // → src/main/java/com/pajk/provider3/service/UserService.java
        
        // 3. 从Git获取源文件内容
        String sourceCode = gitApiClient.getFileContent(
            project.getGitRepoUrl(),
            sourceFilePath,
            project.getGitBranch() != null ? project.getGitBranch() : "main"
        );
        
        if (sourceCode == null) {
            log.debug("无法获取源文件: {}", sourceFilePath);
            return null;
        }
        
        // 4. 解析源代码
        return parseParameterNamesFromSource(sourceCode, interfaceName, methodName);
    }
    
    /**
     * 使用JavaParser解析源代码
     */
    private List<String> parseParameterNamesFromSource(
        String sourceCode, 
        String interfaceName, 
        String methodName
    ) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            
            String simpleClassName = getSimpleClassName(interfaceName);
            
            // 查找接口声明
            Optional<ClassOrInterfaceDeclaration> interfaceDecl = cu.findFirst(
                ClassOrInterfaceDeclaration.class,
                c -> c.getNameAsString().equals(simpleClassName)
            );
            
            if (!interfaceDecl.isPresent()) {
                return null;
            }
            
            // 查找方法
            List<MethodDeclaration> methods = interfaceDecl.get()
                .getMethodsByName(methodName);
            
            if (methods.isEmpty()) {
                return null;
            }
            
            // 提取参数名（取第一个匹配的方法）
            MethodDeclaration method = methods.get(0);
            return method.getParameters().stream()
                .map(p -> p.getNameAsString())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("解析源代码失败: {}.{}", interfaceName, methodName, e);
            return null;
        }
    }
    
    private String interfaceNameToFilePath(String interfaceName) {
        return "src/main/java/" + interfaceName.replace('.', '/') + ".java";
    }
    
    private String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}
```

---

### 3. GitApiClient （Git API客户端）

**职责**: 统一封装GitLab和GitHub API调用

**接口**:
```java
public interface GitApiClient {
    /**
     * 获取文件内容
     * @param repoUrl 仓库URL (https://github.com/user/repo 或 https://gitlab.com/user/repo)
     * @param filePath 文件路径 (src/main/java/...)
     * @param branch 分支名
     * @return 文件内容
     */
    String getFileContent(String repoUrl, String filePath, String branch);
}
```

**实现（支持GitLab和GitHub）**:
```java
@Service
@Slf4j
public class GitApiClientImpl implements GitApiClient {
    
    @Value("${git.gitlab.token:}")
    private String gitlabToken;
    
    @Value("${git.github.token:}")
    private String githubToken;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public String getFileContent(String repoUrl, String filePath, String branch) {
        if (repoUrl.contains("gitlab")) {
            return getFromGitLab(repoUrl, filePath, branch);
        } else if (repoUrl.contains("github")) {
            return getFromGitHub(repoUrl, filePath, branch);
        } else {
            throw new IllegalArgumentException("不支持的Git服务: " + repoUrl);
        }
    }
    
    private String getFromGitLab(String repoUrl, String filePath, String branch) {
        // GitLab API: GET /projects/:id/repository/files/:file_path/raw
        String projectPath = extractProjectPath(repoUrl);
        String encodedFilePath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        
        String apiUrl = String.format(
            "https://gitlab.com/api/v4/projects/%s/repository/files/%s/raw?ref=%s",
            URLEncoder.encode(projectPath, StandardCharsets.UTF_8),
            encodedFilePath,
            branch
        );
        
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.isNotEmpty(gitlabToken)) {
            headers.set("PRIVATE-TOKEN", gitlabToken);
        }
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl, HttpMethod.GET, entity, String.class
        );
        
        return response.getBody();
    }
    
    private String getFromGitHub(String repoUrl, String filePath, String branch) {
        // GitHub API: GET /repos/:owner/:repo/contents/:path
        String[] parts = extractOwnerAndRepo(repoUrl);
        String owner = parts[0];
        String repo = parts[1];
        
        String apiUrl = String.format(
            "https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
            owner, repo, filePath, branch
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3.raw");
        if (StringUtils.isNotEmpty(githubToken)) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl, HttpMethod.GET, entity, String.class
        );
        
        return response.getBody();
    }
}
```

---

## 数据流

### 完整流程

```
1. 用户上传JAR包并选择创建虚拟节点
        ↓
2. JarScannerService.scanJar()   扫描JAR，提取接口和方法
        ↓
3. ParameterNameResolver.getParameterNames()   解析参数名
        ↓
    ┌─ 3.1 SourceCodeAnalyzer.getParameterNames()
    │   ├─ 查找项目配置（Git仓库URL）
    │   ├─ GitApiClient.getFileContent()   从Git获取源文件
    │   └─ JavaParser.parse()   解析Java源码
    │       └─ 返回 ["userId", "orderId", ...]
    │
    ├─ 3.2 (失败) ClassFileAnalyzer.getParameterNames()
    │       └─ ASM读取MethodParameters
    │
    └─ 3.3 (失败) 返回 ["arg0", "arg1", ...]
        ↓
4. 创建MCP Schema with正确的参数名
        ↓
5. 注册虚拟节点到Nacos
```

---

## 配置示例

### application.yml

```yaml
# Git API配置
git:
  gitlab:
    token: ${GITLAB_TOKEN:}  # GitLab Personal Access Token
    api-url: https://gitlab.com/api/v4
  
  github:
    token: ${GITHUB_TOKEN:}  # GitHub Personal Access Token
    api-url: https://api.github.com
  
  # 缓存配置
  cache:
    enabled: true
    ttl: 3600  # 1小时
    max-size: 1000

# 参数名解析策略
parameter-name-resolution:
  strategy: auto  # auto, source-code-only, class-file-only
  fallback-to-default: true
  log-failures: true
```

### 项目配置（数据库）

在 `zk_dubbo_project` 表添加字段：

```sql
ALTER TABLE zk_dubbo_project ADD COLUMN git_repo_url VARCHAR(500);
ALTER TABLE zk_dubbo_project ADD COLUMN git_branch VARCHAR(100) DEFAULT 'main';
ALTER TABLE zk_dubbo_project ADD COLUMN git_token VARCHAR(200);  -- 可选，加密存储
```

---

## API设计

### REST API

```http
### 手动触发参数名刷新
POST /api/admin/refresh-parameter-names
Content-Type: application/json

{
  "projectId": "demo-provider3",
  "interfaceName": "com.pajk.provider3.service.UserService"
}
```

---

## 依赖清单

### pom.xml 添加

```xml
<!-- JavaParser -->
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.25.7</version>
</dependency>

<!-- Guava Cache (if not already included) -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>
```

---

## 总结

### 优势

1. ✅ **最可靠**: 源代码永远包含真实参数名
2. ✅ **向后兼容**: 三级降级确保老项目也能工作
3. ✅ **额外价值**: 可以扩展提取JavaDoc、参数说明等
4. ✅ **可扩展**: 未来可支持更多Git服务

### 实施优先级

**Phase 1** (立即): demo-provider3 使用 `-parameters` 验证
**Phase 2** (下一步): 实施源代码分析方案
**Phase 3** (未来): 扩展到提取JavaDoc等更多元数据

---

**文档创建时间**: 2026-02-11 19:10  
**追溯**: REQ-20260211-002
