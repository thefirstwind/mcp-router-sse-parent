# CI/CD 流程深度分析

> **项目名称**: MCP Router SSE Parent  
> **分析日期**: 2026-02-11  
> **分析目标**: CI/CD 流程、自动化测试、部署pipeline

---

## 目录

1. [CI/CD 概述](#1-cicd-概述)
2. [GitHub Actions Workflows](#2-github-actions-workflows)
3. [本地测试脚本](#3-本地测试脚本)
4. [构建流程](#4-构建流程)
5. [测试策略](#5-测试策略)
6. [部署流程](#6-部署流程)
7. [质量保障](#7-质量保障)

---

## 1. CI/CD 概述

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      代码提交触发                              │
└────────────────────┬───────────────────────────────────────────┘
                     │
          ┌──────────┼──────────┐
          │                     │
   ┌──────▼──────┐      ┌──────▼──────┐
   │   GitHub    │      │    本地      │
   │   Actions   │      │   验证       │
   └──────┬──────┘      └──────┬──────┘
          │                     │
   ┌──────▼──────────────────────▼──────┐
   │                                     │
   │        多层次测试验证                │
   │                                     │
   │  ┌──────────────────────────────┐  │
   │  │  1. 单元测试                  │  │
   │  │  2. 集成测试                  │  │
   │  │  3. SSE 协议测试              │  │
   │  │  4. Streamable 会话测试       │  │
   │  │  5. 核心能力验证              │  │
   │  └──────────────────────────────┘  │
   └──────┬──────────────────────────────┘
          │
   ┌──────▼──────┐
   │  构建产物    │
   │  - JAR      │
   │  - Docker   │
   │  - Docs     │
   └──────┬──────┘
          │
   ┌──────▼──────┐
   │   部署       │
   │  - 测试环境  │
   │  - 生产环境  │
   └─────────────┘
```

### 1.2 CI/CD 工具栈

| 工具 | 用途 | 版本 |
|------|------|------|
| **GitHub Actions** | CI/CD 主平台 | - |
| **Maven** | 构建工具 | 3.6+ |
| **JUnit** | 单元测试 | 5.x |
| **curl** | API 测试 | - |
| **jq** | JSON 处理 | 1.6+ |
| **Redis** | 会话存储 | 7.x |
| **MkDocs** | 文档生成 | - |

---

## 2. GitHub Actions Workflows

### 2.1 Workflow 总览

项目有 **4 个核心 Workflows**：

| Workflow | 触发条件 | 目的 | 状态 |
|---------|---------|------|------|
| `maven-build.yml` | 手动触发 | 标准 Maven 构建 | ⚠️ 已禁用自动触发 |
| `multi-module-build.yml` | `main`, `develop` 分支推送/PR | 多模块增量构建 | ✅ 活跃 |
| `test-streamable-session.yml` | 特定路径变更 | Streamable 协议测试 | ✅ 活跃 |
| `docs.yml` | `docs/` 变更 | 文档部署 | ✅ 活跃 |

### 2.2 Maven Build Workflow

#### 配置详情

```yaml
# .github/workflows/maven-build.yml
name: Maven Build and Test

on:
  workflow_dispatch:  # 仅手动触发

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'corretto'
          cache: maven
      
      # 1. 构建 spring-ai-alibaba 依赖
      - name: Build spring-ai-alibaba dependencies
        run: |
          cd spring-ai-alibaba
          mvn clean install -DskipTests
      
      # 2. 构建主项目
      - name: Build main project
        run: mvn -B clean install -DskipTests
      
      # 3. 运行测试
      - name: Run Tests
        run: mvn test -Dmaven.test.failure.ignore=true
      
      # 4. 发布测试报告
      - name: Publish Test Report
        uses: dorny/test-reporter@v1
        with:
          name: Maven Test Results
          path: '**/surefire-reports/*.xml'
          reporter: java-junit
```

#### 关键特性

1. **依赖顺序**：先构建 `spring-ai-alibaba`，再构建主项目
2. **测试容错**：使用 `continue-on-error: true`，不阻塞整个流程
3. **缓存优化**：Maven 依赖缓存，加速构建

#### 为什么禁用自动触发？

```yaml
# 原因：专注于多模块增量构建和文档
on:
  # 已注释掉
  # push:
  #   branches: [ main, develop ]
  workflow_dispatch:  # 改为手动触发
```

---

### 2.3 Multi-Module Build Workflow ⭐️

这是**最核心的 CI 流程**，实现了智能增量构建。

#### 工作流程图

```
┌─────────────────────────────────────────┐
│  Step 1: 检测变更的模块                   │
│  (使用 paths-filter)                    │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Step 2: 并行构建变更的模块               │
│  - mcp-router-v3                        │
│  - mcp-server-v3                        │
│  - mcp-server-v4                        │
│  - mcp-server-v6                        │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Step 3: 对每个变更模块                  │
│  - mvn clean install                    │
│  - mvn test                             │
│  - 上传构建产物 (JAR)                    │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Step 4: (main 分支) 全量构建验证        │
│  - mvn clean install (整个项目)         │
│  - mvn test (所有模块)                  │
└─────────────────────────────────────────┘
```

#### 关键代码分析

```yaml
# 1. 变更检测 Job
detect-changes:
  runs-on: ubuntu-latest
  outputs:
    mcp-router: ${{ steps.filter.outputs.mcp-router }}
    mcp-server-v3: ${{ steps.filter.outputs.mcp-server-v3 }}
    # ...
  
  steps:
    - uses: dorny/paths-filter@v2
      id: filter
      with:
        filters: |
          mcp-router:
            - 'mcp-router-v3/**'
            - 'pom.xml'
          mcp-server-v3:
            - 'mcp-server-v3/**'
            - 'pom.xml'

# 2. 矩阵构建 Job
build-and-test:
  needs: detect-changes
  strategy:
    matrix:
      module: 
        - mcp-router-v3
        - mcp-server-v3
        - mcp-server-v4
        - mcp-server-v6
  
  steps:
    - name: Build ${{ matrix.module }}
      if: steps.check.outputs.changed == 'true'
      run: |
        cd ${{ matrix.module }}
        mvn clean install -DskipTests
    
    - name: Upload build artifacts
      if: github.ref == 'refs/heads/main'
      uses: actions/upload-artifact@v3
      with:
        name: ${{ matrix.module }}-jar
        path: ${{ matrix.module }}/target/*.jar
        retention-days: 7
```

#### 优势

✅ **增量构建**：只构建变更的模块，节省时间  
✅ **并行执行**：多个模块同时构建  
✅ **产物保留**：main 分支的 JAR 保留 7 天  
✅ **全量验证**：main 分支额外进行全量构建

---

### 2.4 Streamable Session Test Workflow ⭐️

专门测试 **Streamable 协议**和 **SSE 会话管理**。

#### 触发条件

```yaml
on:
  pull_request:
    paths:
      - 'mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java'
      - 'mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpSessionService.java'
      - 'test_streamable_*.sh'
  push:
    branches:
      - main
      - 'bugfix/fix-streamable-*'
```

#### 服务依赖

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - 6379:6379
    options: >-
      --health-cmd "redis-cli ping"
      --health-interval 10s
      --health-timeout 5s
      --health-retries 5
```

#### 测试流程

```yaml
steps:
  # 1. 构建 mcp-router-v3
  - name: Build mcp-router-v3
    run: |
      cd mcp-router-v3
      mvn clean package -DskipTests
  
  # 2. 启动 mcp-router-v3
  - name: Start mcp-router-v3
    run: |
      cd mcp-router-v3
      nohup mvn spring-boot:run > router.log 2>&1 &
      timeout 60 bash -c 'until curl -f http://localhost:8052/actuator/health; do sleep 2; done'
  
  # 3. 启动 mcp-server-v6
  - name: Build and start mcp-server-v6
    run: |
      cd mcp-server-v6
      mvn clean package -DskipTests
      nohup mvn spring-boot:run > server.log 2>&1 &
      sleep 10
  
  # 4. 运行快速测试
  - name: Run quick tests
    run: ./test_streamable_session.sh
  
  # 5. 运行综合测试
  - name: Run comprehensive tests
    run: ./test_streamable_comprehensive.sh
  
  # 6. 上传测试结果
  - name: Upload test results
    if: always()
    uses: actions/upload-artifact@v4
    with:
      name: test-results
      path: |
        test_results.log
        mcp-router-v3/router.log
        mcp-server-v6/server.log
  
  # 7. PR 评论测试结果
  - name: Comment PR with results
    if: github.event_name == 'pull_request'
    uses: actions/github-script@v7
    with:
      script: |
        const fs = require('fs');
        let testResults = fs.readFileSync('test_results.log', 'utf8');
        
        github.rest.issues.createComment({
          issue_number: context.issue.number,
          owner: context.repo.owner,
          repo: context.repo.repo,
          body: `## 🧪 Streamable Session Management Tests\n\`\`\`\n${testResults}\n\`\`\``
        });
```

#### 测试覆盖

| 测试类别 | 测试内容 | 脚本 |
|---------|---------|------|
| **协议完整性** | Session 消息格式、响应头 | `test_streamable_comprehensive.sh` |
| **Session ID** | 头部解析、查询参数、自动生成 | 同上 |
| **生命周期** | Redis 存储、TTL 刷新 | 同上 |
| **多路径** | 不同服务名路径、查询参数 | 同上 |
| **SSE 兼容** | 验证 SSE 模式未受影响 | 同上 |
| **错误处理** | 无效服务、畸形 JSON | 同上 |
| **端到端** | 完整工作流测试 | 同上 |
| **并发** | 10 个并发连接 | 同上 |

---

### 2.5 Documentation Workflow

自动构建并部署项目文档到 GitHub Pages。

```yaml
# .github/workflows/docs.yml
name: Deploy MkDocs Documentation

on:
  push:
    branches:
      - main
    paths:
      - 'docs/**'
      - 'mkdocs.yml'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # 获取完整历史
      
      - uses: actions/setup-python@v4
        with:
          python-version: 3.x
      
      - name: Install dependencies
        run: |
          pip install mkdocs-material
          pip install mkdocs-git-revision-date-localized-plugin
      
      - name: Build documentation
        run: mkdocs build
      
      - name: Deploy to GitHub Pages
        run: mkdocs gh-deploy --force
```

#### 文档结构

```
docs/
├── index.md                    # 首页
├── getting-started/            # 快速开始
├── architecture/               # 架构文档
├── api/                        # API 文档
├── features/                   # 功能文档
│   └── streamable-session-management.md
└── deployment/                 # 部署指南
```

---

## 3. 本地测试脚本

### 3.1 脚本分类

项目包含 **24 个测试脚本**，分为以下类别：

#### A. 核心能力验证脚本

| 脚本 | 功能 | 测试层次 |
|------|------|---------|
| `testScript/verify-core-capabilities.sh` | **完整的核心能力验证** | ⭐️⭐️⭐️⭐️⭐️ |
| `testScript/verify-mcp-router.sh` | Router 核心功能 | ⭐️⭐️⭐️⭐️ |
| `testScript/verify-sse-connection.sh` | SSE 连接验证 | ⭐️⭐️⭐️ |

#### B. Streamable 协议测试脚本

| 脚本 | 功能 | 测试深度 |
|------|------|---------|
| `test_streamable_comprehensive.sh` | **完整的 Streamable 测试** | ⭐️⭐️⭐️⭐️⭐️ |
| `test_streamable_session.sh` | 快速会话测试 | ⭐️⭐️⭐️ |

#### C. 集成测试脚本

| 脚本 | 功能 |
|------|------|
| `testScript/test-mcp-integration.sh` | MCP 集成测试 |
| `testScript/test-mcp-demo.sh` | MCP Demo 测试 |
| `testScript/test-mcp-jsonrpc.sh` | JSON-RPC 协议测试 |

#### D. 项目启动脚本

| 脚本 | 功能 |
|------|------|
| `start-all-projects.sh` | **启动所有项目** |
| `stop-all-projects.sh` | 停止所有项目 |
| `testScript/start-with-ip.sh` | IP 修复版启动 |

#### E. 特定功能测试

| 脚本 | 功能 |
|------|------|
| `mcp-router-v3/quick-test-sse.sh` | 快速 SSE 测试 |
| `mcp-router-v3/test-endpoint-name-mapping.sh` | Endpoint 映射测试 |
| `testScript/test-ip-fix.sh` | IP 配置修复测试 |

### 3.2 核心脚本深度分析

#### 3.2.1 verify-core-capabilities.sh

**最全面的系统验证脚本**，包含 **7 大验证部分，12 项测试**。

```bash
#!/bin/bash
# 验证目标: 完整的端到端MCP协议功能验证

# 配置参数
CLIENT_HOST="localhost:8070"
ROUTER_HOST="localhost:8050"
SERVER_HOST="192.168.31.47:8061"

# 测试矩阵
tests=(
    "基础健康检查:$all_healthy"
    "Router MCP端点:$router_mcp_ok"
    "Server MCP端点:$server_mcp_ok"
    "Client工具发现:$client_discovery_ok"
    "Router工具发现:$router_discovery_ok"
    "Client端到端调用:$client_call_ok"
    "Router直接调用:$router_call_ok"
    "数据库添加操作:$add_person_ok"
    "数据库验证查询:$verify_ok"
    "系统信息获取:$system_info_ok"
    "服务器列表获取:$list_servers_ok"
    "SSE协议验证:$sse_ok"
)
```

**验证流程**：

```
第一部分：基础健康检查
├─ 检查 mcp-client (8070)
├─ 检查 mcp-router (8050)
└─ 检查 mcp-server-v2 (8061)

第二部分：MCP协议层验证
├─ 验证 Router MCP SSE 端点
└─ 验证 Server MCP SSE 端点

第三部分：工具发现与调用验证
├─ 通过 Client 验证工具发现
│  └─ 检查关键工具: getAllPersons, addPerson, deletePerson
└─ 直接验证 Router 工具发现
   ├─ 发送 initialize 请求
   └─ 发送 tools/list 请求

第四部分：端到端工具调用验证
├─ 通过 Client 调用 getAllPersons
└─ 直接通过 Router 调用 getAllPersons

第五部分：数据库操作验证
├─ 添加新人员
└─ 验证添加结果

第六部分：系统信息验证
├─ 获取系统信息
└─ 列出已注册服务器

第七部分：SSE协议验证
└─ 验证 SSE 连接建立
```

**成功率计算**：

```bash
success_rate=$((passed_tests * 100 / total_tests))

if [ $success_rate -ge 90 ]; then
    echo "🎉 系统核心能力验证优秀！"
elif [ $success_rate -ge 70 ]; then
    echo "👍 系统核心能力验证良好"
elif [ $success_rate -ge 50 ]; then
    echo "⚠️  系统核心能力验证一般"
else
    echo "❌ 系统核心能力验证不足"
fi
```

---

#### 3.2.2 test_streamable_comprehensive.sh

**651 行的完整 Streamable 协议测试套件**。

**测试分组**：

```
A. Streamable 协议完整性测试 (4 tests)
├─ 验证第一条 NDJSON 消息包含 session 信息
├─ 验证响应头包含 Mcp-Session-Id
├─ 测试不同的 Accept 头
└─ 验证 NDJSON 格式

B. Session ID 解析测试 (3 tests)
├─ 测试各种请求头格式
│  ├─ Mcp-Session-Id: xxx
│  ├─ X-Mcp-Session-Id: xxx
│  ├─ mcp-session-id: xxx (小写)
│  ├─ Session-Id: xxx
│  └─ X-Session-Id: xxx
├─ 测试查询参数 sessionId
└─ 测试无 sessionId 时自动生成

C. Session 生命周期测试 (需 Redis)
├─ 创建 Session 并验证 Redis 存储
├─ 验证 Session TTL (~30 分钟)
├─ 测试 Session TTL 刷新
└─ (如 Redis 不可用则跳过)

D. 不同路径和服务测试 (3 tests)
├─ GET /mcp/{serviceName}
├─ POST /mcp/{serviceName}/message
└─ GET /mcp?serviceName=xxx

E. SSE 模式兼容性测试 (1 test)
└─ 验证 SSE 模式未受影响

F. 错误处理测试 (2 tests)
├─ 测试无效的服务名
└─ 测试畸形 JSON 请求

G. 端到端完整流程测试 (1 test)
├─ 步骤 1: 建立连接并获取 sessionId
├─ 步骤 2: 发送 initialize
└─ 步骤 3: 发送 tools/list

H. 并发连接测试 (1 test)
└─ 测试 10 个并发连接
   ├─ 验证所有连接成功
   └─ 验证 sessionId 唯一性
```

**结果统计**：

```bash
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# 最终报告
if [[ $FAILED_TESTS -eq 0 ]]; then
    echo "🎉 所有测试通过！"
    exit 0
else
    echo "❌ 部分测试失败"
    exit 1
fi
```

---

## 4. 构建流程

### 4.1 本地构建流程

```bash
# 1. 清理并构建整个项目
mvn clean install

# 2. 构建特定模块
cd mcp-router-v3
mvn clean package -DskipTests

# 3. 运行测试
mvn test

# 4. 查看构建产物
ls -lh target/*.jar
```

### 4.2 Maven 多模块结构

```xml
<!-- pom.xml (parent) -->
<modules>
  <module>mcp-router-v3</module>
  <module>mcp-server-v3</module>
  <module>mcp-server-v4</module>
  <module>mcp-server-v6</module>
</modules>
```

### 4.3 构建生命周期

```
mvn clean
  └─> 清理 target/ 目录

mvn compile
  ├─> 编译 src/main/java
  └─> 处理 src/main/resources

mvn test-compile
  └─> 编译 src/test/java

mvn test
  ├─> 运行单元测试
  └─> 生成测试报告 (surefire-reports/)

mvn package
  ├─> 打包为 JAR
  └─> 包含 manifest, dependencies

mvn install
  └─> 安装到本地仓库 (~/.m2/repository)

mvn deploy
  └─> 部署到远程仓库 (需配置)
```

---

## 5. 测试策略

### 5.1 测试金字塔

```
        ┌─────────────┐
        │  手动探索    │  1%
        │  测试        │
        └─────────────┘
       ┌───────────────┐
       │   端到端测试   │  10%
       │  (E2E Tests)  │
       └───────────────┘
     ┌───────────────────┐
     │    集成测试        │  20%
     │ (Integration)     │
     └───────────────────┘
   ┌───────────────────────┐
   │       单元测试         │  69%
   │   (Unit Tests)        │
   └───────────────────────┘
```

### 5.2 测试覆盖矩阵

| 测试层级 | 工具 | 覆盖范围 | 执行频率 |
|---------|------|---------|---------|
| **单元测试** | JUnit 5 | 单个类/方法 | 每次提交 |
| **集成测试** | Spring Boot Test | 多个组件交互 | 每次提交 |
| **API 测试** | curl + jq | HTTP/SSE 端点 | 每次 PR |
| **协议测试** | 自定义脚本 | MCP/Streamable 协议 | 关键路径变更 |
| **端到端测试** | 自动化脚本 | 完整业务流程 | 每日/手动 |
| **性能测试** | 并发脚本 | 并发连接、响应时间 | 发布前 |

### 5.3 测试数据管理

#### 测试数据库

```sql
-- 使用 H2 内存数据库用于测试
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
```

#### 测试 Redis

```yaml
# 使用 Docker 启动测试 Redis
docker run -d -p 6379:6379 redis:7-alpine
```

### 5.4 测试环境隔离

```yaml
# application-test.yml
spring:
  profiles:
    active: test
  
  datasource:
    url: jdbc:h2:mem:testdb
  
  redis:
    host: localhost
    port: 6379
    database: 15  # 使用独立的数据库编号
  
nacos:
  server-addr: localhost:8848
  namespace: test  # 测试命名空间
```

---

## 6. 部署流程

### 6.1 部署环境

| 环境 | 用途 | 触发条件 |
|------|------|---------|
| **开发环境** | 本地开发 | 手动启动 |
| **测试环境** | 自动化测试 | PR 创建 |
| **预生产环境** | 发布验证 | main 分支合并 |
| **生产环境** | 正式服务 | Tag 发布 |

### 6.2 部署流水线 (未来)

```yaml
# .github/workflows/deploy.yml (示例)
name: Deploy to Production

on:
  push:
    tags:
      - 'v*'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      
      - name: Build Docker Image
        run: |
          docker build -t mcp-router:${{ github.ref_name }} .
      
      - name: Push to Docker Registry
        run: |
          docker push mcp-router:${{ github.ref_name }}
      
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/mcp-router \
            mcp-router=mcp-router:${{ github.ref_name }}
```

### 6.3 本地部署

```bash
# 1. 启动基础服务
zkServer.sh start          # ZooKeeper
startup.sh -m standalone   # Nacos
mysql.server start         # MySQL

# 2. 启动应用
./start-all-projects.sh

# 3. 验证部署
./testScript/verify-core-capabilities.sh
```

---

## 7. 质量保障

### 7.1 代码质量检查

#### 编译时检查

```xml
<!-- pom.xml -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <source>17</source>
    <target>17</target>
    <compilerArgs>
      <arg>-Xlint:all</arg>  <!-- 启用所有警告 -->
      <arg>-Werror</arg>     <!-- 警告视为错误 -->
    </compilerArgs>
  </configuration>
</plugin>
```

#### 静态代码分析 (未来)

```yaml
# SonarQube 集成示例
- name: SonarQube Scan
  run: |
    mvn sonar:sonar \
      -Dsonar.projectKey=mcp-router \
      -Dsonar.host.url=${{ secrets.SONAR_HOST }} \
      -Dsonar.login=${{ secrets.SONAR_TOKEN }}
```

### 7.2 安全扫描 (未来)

```yaml
# 依赖漏洞扫描
- name: Dependency Check
  run: mvn dependency-check:check

# Docker 镜像扫描
- name: Trivy Scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: 'mcp-router:latest'
    severity: 'CRITICAL,HIGH'
```

### 7.3 发布清单

在发布到生产环境前，确保：

- [ ] ✅ 所有 CI 测试通过
- [ ] ✅ 代码审查完成
- [ ] ✅ 文档已更新
- [ ] ✅ 变更日志已记录
- [ ] ✅ 数据库迁移脚本准备
- [ ] ✅ 回滚方案准备
- [ ] ✅ 性能测试通过
- [ ] ✅ 安全扫描无高危漏洞

---

## 附录

### A.1 常用命令速查

```bash
# 本地测试
mvn clean test                                    # 运行所有测试
mvn test -Dtest=McpRouterServiceTest              # 运行特定测试
./test_streamable_comprehensive.sh                # Streamable 综合测试
./testScript/verify-core-capabilities.sh          # 核心能力验证

# 本地构建
mvn clean install                                 # 构建并安装到本地仓库
mvn clean package -DskipTests                     # 快速打包(跳过测试)
mvn dependency:tree                               # 查看依赖树
mvn dependency:analyze                            # 分析依赖

# 本地运行
./start-all-projects.sh                           # 启动所有服务
./stop-all-projects.sh                            # 停止所有服务
tail -f logs/zkInfo.log                           # 查看日志

# GitHub Actions
gh workflow run maven-build.yml                   # 手动触发构建
gh run list                                       # 查看运行列表
gh run view <run-id>                              # 查看运行详情
```

### A.2 故障排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| CI 构建失败 | 依赖下载失败 | 检查 Maven 仓库配置 |
| 测试超时 | 服务启动慢 | 增加 timeout 时间 |
| Redis 测试跳过 | Redis 未启动 | 启动 Redis: `docker run -d -p 6379:6379 redis:7-alpine` |
| Nacos 连接失败 | 服务未注册 | 检查 Nacos 配置和网络 |

### A.3 性能优化建议

1. **Maven 构建加速**
   ```bash
   mvn clean install -T 4  # 4 线程并行构建
   mvn clean install -o    # 离线模式(使用缓存)
   ```

2. **Docker 缓存优化**
   ```dockerfile
   # 分层复制，提高缓存命中率
   COPY pom.xml .
   RUN mvn dependency:go-offline
   COPY src/ src/
   RUN mvn package
   ```

3. **GitHub Actions 缓存**
   ```yaml
   - uses: actions/cache@v3
     with:
       path: ~/.m2/repository
       key: maven-${{ hashFiles('**/pom.xml') }}
   ```

---

**文档版本**: 2.0  
**最后更新**: 2026-02-11  
**维护者**: ZkInfo Team  
**相关文档**: [SYSTEM_ARCHITECTURE_ANALYSIS.md](./SYSTEM_ARCHITECTURE_ANALYSIS.md)
