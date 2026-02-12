# 文档追溯体系：需求-设计-代码-测试-文档全链路

> **项目名称**: MCP Router SSE Parent  
> **文档版本**: 1.0  
> **创建日期**: 2026-02-11  
> **目标**: 建立需求、设计、代码、测试、文档之间的完整追溯关系

---

## 目录

1. [体系概述](#1-体系概述)
2. [文档分类体系](#2-文档分类体系)
3. [全链路追溯流程](#3-全链路追溯流程)
4. [实际案例分析](#4-实际案例分析)
5. [工具和自动化](#5-工具和自动化)
6. [最佳实践](#6-最佳实践)

---

## 1. 体系概述

### 1.1 文档追溯的核心价值

```
需求变更 → 能快速定位影响的设计、代码、测试
代码修改 → 能追溯到原始需求和设计决策
测试失败 → 能找到对应的需求和实现
文档过时 → 能识别需要更新的部分
```

### 1.2 完整的追溯链

```
┌─────────────────────────────────────────────────────────────┐
│                    1. 需求 (Requirements)                    │
│  - 用户故事 (User Stories)                                   │
│  - 功能需求 (Feature Requests)                               │
│  - 业务场景 (Business Scenarios)                             │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                2. 架构设计 (Architecture Design)              │
│  - ADR (Architecture Decision Records)                      │
│  - 系统架构文档                                               │
│  - 接口设计文档                                               │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                   3. 详细设计 (Detail Design)                │
│  - 类图/时序图                                                │
│  - 数据库设计                                                 │
│  - API 规范                                                  │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                      4. 代码实现 (Code)                      │
│  - Java 类/接口                                              │
│  - 配置文件                                                  │
│  - SQL 脚本                                                  │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                      5. 测试验证 (Test)                      │
│  - 单元测试                                                  │
│  - 集成测试                                                  │
│  - 端到端测试                                                │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                    6. 用户文档 (Documentation)               │
│  - 使用手册                                                  │
│  - API 文档                                                  │
│  - 故障排除                                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 文档分类体系

### 2.1 按 Diátaxis 框架分类

本项目采用 **Diátaxis 文档框架**，将文档分为四大类：

```
┌──────────────────────────────────────────────┐
│         Diátaxis 文档四象限                   │
├─────────────────────┬────────────────────────┤
│  学习导向            │   目标导向              │
│  📚 TUTORIALS       │   🛠️ HOW-TO GUIDES     │
│  (Teaching)         │   (Guiding)            │
│  - 快速开始          │   - 添加MCP Server     │
│  - 第一个Agent       │   - 集成Gemini         │
│  - 基础概念          │   - 故障排除           │
├─────────────────────┼────────────────────────┤
│  理解导向            │   信息导向              │
│  💡 EXPLANATION     │   📋 REFERENCE         │
│  (Explaining)       │   (Informing)          │
│  - 架构设计          │   - API文档            │
│  - 设计决策(ADR)     │   - 配置参考           │
│  - 工作流对比        │   - 命令行参考         │
└─────────────────────┴────────────────────────┘
```

### 2.2 项目文档目录结构

```
mcp-router-sse-parent/
│
├── 📚 docs/                              # 用户文档主目录
│   ├── README.md                         # 📖 文档导航中心（单一入口）
│   │
│   ├── tutorials/                        # 🎓 教程（学习导向）
│   │   ├── quick-start.md               # 5分钟快速开始
│   │   └── first-mcp-server.md          # 第一个MCP Server
│   │
│   ├── how-to-guides/                   # 🛠️ 操作指南（目标导向）
│   │   ├── add-mcp-server.md           # 如何添加MCP Server
│   │   ├── integrate-gemini.md         # 如何集成Gemini
│   │   └── troubleshooting.md          # 故障排除
│   │
│   ├── explanations/                    # 💡 说明文档（理解导向）
│   │   ├── architecture.md             # 架构说明
│   │   ├── workflow-comparison.md      # 工作流对比
│   │   └── gemini-plan.md              # Gemini集成计划
│   │
│   ├── reference/                       # 📋 参考文档（信息导向）
│   │   ├── api/                        # API文档
│   │   ├── configuration.md            # 配置参考
│   │   └── cli-commands.md             # 命令行参考
│   │
│   ├── adr/                             # 🏛️ 架构决策记录
│   │   ├── README.md                   # ADR索引
│   │   └── 001-streamable-session.md   # ADR-001
│   │
│   └── features/                        # ✨ 功能文档
│       ├── README.md                   # 功能索引
│       └── streamable-session-management.md
│
├── 🤖 .agent/workflows/                  # AI Agent工作流（开发流程）
│   ├── add-mcp-server.md               # 添加MCP Server工作流
│   ├── add-agent-workflow.md           # 添加Agent工作流
│   └── review.md                       # 代码审查工作流
│
├── 📊 项目分析文档（根目录）
│   ├── PROJECT_ANALYSIS.md             # 项目分析总览
│   ├── SYSTEM_ARCHITECTURE_ANALYSIS.md # 系统架构分析
│   ├── CICD_PROCESS_ANALYSIS.md        # CI/CD流程分析
│   └── DOCUMENTATION_TRACEABILITY_SYSTEM.md  # 本文档
│
├── 🔧 scripts/                          # 脚本和工具
│   ├── README.md                       # 脚本说明
│   └── maintenance/                    # 维护脚本
│
└── 📝 模块级文档
    ├── mcp-router-v3/README.md         # Router模块文档
    ├── mcp-server-v6/README.md         # Server模块文档
    └── zk-mcp-parent/zkInfo/
        ├── CURL_VALIDATION_GUIDE.md    # API验证指南
        └── README.md                   # zkInfo模块文档
```

---

## 3. 全链路追溯流程

### 3.1 完整的开发流程（从需求到文档）

```
阶段 1: 需求分析
├─ 输入: 用户需求、业务场景
├─ 输出: 用户故事、功能需求文档
├─ 位置: GitHub Issues / 需求文档
└─ 示例: "需要支持Streamable协议以兼容MCP 2024-11-05标准"

         ↓

阶段 2: 架构设计
├─ 输入: 功能需求
├─ 输出: ADR、架构图、系统设计文档
├─ 位置: docs/adr/, docs/explanations/architecture.md
└─ 示例: ADR-001 Streamable协议双重Session ID传递机制

         ↓

阶段 3: 详细设计
├─ 输入: 架构设计
├─ 输出: 类图、时序图、接口定义、数据库设计
├─ 位置: docs/reference/, 设计文档
└─ 示例: McpSessionService接口设计、Redis存储方案

         ↓

阶段 4: 代码实现
├─ 输入: 详细设计
├─ 输出: Java代码、配置文件、SQL脚本
├─ 位置: src/main/java/, src/main/resources/
└─ 示例: McpSessionService.java, McpRouterServerConfig.java

         ↓

阶段 5: 测试验证
├─ 输入: 代码实现、设计文档
├─ 输出: 测试代码、测试报告
├─ 位置: src/test/, testScript/, .github/workflows/
└─ 示例: test_streamable_comprehensive.sh

         ↓

阶段 6: 文档编写
├─ 输入: 所有上述产物
├─ 输出: 用户文档、API文档、操作手册
├─ 位置: docs/
└─ 示例: docs/features/streamable-session-management.md

         ↓

阶段 7: 发布和维护
├─ 输入: 完整的交付物
├─ 输出: 发布说明、变更日志
├─ 位置: CHANGELOG.md, GitHub Releases
└─ 示例: v1.0.0 Release Notes
```

### 3.2 双向追溯机制

#### 向前追溯（Forward Traceability）

从需求追溯到实现：

```
需求 ID → 设计文档 → 代码文件 → 测试用例 → 用户文档
```

#### 向后追溯（Backward Traceability）

从实现追溯到需求：

```
代码文件 → 设计文档 → 需求 ID → 原始用户故事
```

---

## 4. 实际案例分析

### 案例 1: Streamable 协议支持

让我们通过一个真实案例展示完整的追溯链路。

#### 4.1 需求阶段

**需求来源**: MCP 标准更新到 2024-11-05 版本

**需求描述**:
```markdown
# Feature Request: 支持 Streamable 协议

## 背景
MCP 2024-11-05 标准引入了 Streamable 协议，要求:
1. 初始响应包含 Session 信息
2. 支持通过 HTTP Header 传递 Session ID
3. 兼容现有 SSE 模式

## 目标
- 实现 Streamable 协议支持
- 保持向后兼容
- 支持 Session 生命周期管理

## 验收标准
- [ ] 第一条消息包含 session 信息
- [ ] 支持多种 Session ID 传递方式
- [ ] Session 存储在 Redis
- [ ] 通过 MCP Inspector 验证
```

**追溯标识**: `REQ-001: Streamable Protocol Support`

#### 4.2 架构设计阶段

**设计文档**: `docs/adr/001-streamable-session-dual-transmission.md`

```markdown
# ADR-001: Streamable 协议双重 Session ID 传递机制

## Status
✅ Accepted

## Context
需要支持 MCP 2024-11-05 Streamable 协议，同时保持向后兼容...

## Decision
采用双重传递机制:
1. 响应体: 第一条 NDJSON 消息包含 session
2. 响应头: Mcp-Session-Id Header

## Implementation
- 代码位置: McpRouterServerConfig.java
- 配置: application.yml
- 测试: test_streamable_comprehensive.sh

## Traceability
- 需求: REQ-001
- 代码: McpRouterServerConfig.java, McpSessionService.java
- 测试: test_streamable_comprehensive.sh
- 文档: docs/features/streamable-session-management.md
```

**追溯标识**: `ADR-001`

#### 4.3 详细设计阶段

**设计文档**: 时序图、类图

```
Session ID 解析流程:
1. 检查请求头: Mcp-Session-Id, X-Mcp-Session-Id, Session-Id, X-Session-Id
2. 检查查询参数: sessionId, session_id
3. 检查路径参数: /mcp/{serviceName}
4. 如果都没有，生成新 Session ID
5. 存储到 Redis (TTL: 30分钟)
```

**涉及类**:
- `McpRouterServerConfig`: 配置 Streamable 端点
- `McpSessionService`: Session 管理服务
- `McpSseController`: SSE 控制器

#### 4.4 代码实现阶段

**文件 1**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

```java
/**
 * Streamable 协议配置
 * 
 * @traceability
 *   - Requirement: REQ-001 Streamable Protocol Support
 *   - Design: ADR-001 Streamable Session Dual Transmission
 *   - Test: test_streamable_comprehensive.sh
 *   - Documentation: docs/features/streamable-session-management.md
 */
@Bean
public RouterFunction<ServerResponse> streamableRoutes() {
    return route()
        .GET("/mcp/{serviceName}", this::handleStreamableConnection)
        .POST("/mcp/message", this::handleMessage)
        .build();
}
```

**文件 2**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpSessionService.java`

```java
/**
 * Session 管理服务
 * 
 * @traceability
 *   - Requirement: REQ-001
 *   - Design: ADR-001
 *   - Test: test_streamable_comprehensive.sh (Section C: Session Lifecycle)
 */
@Service
public class McpSessionService {
    
    /**
     * 创建新 Session 并存储到 Redis
     * TTL: 30 分钟
     */
    public String createSession(String serviceName) {
        String sessionId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
            "mcp:session:" + sessionId,
            serviceName,
            Duration.ofMinutes(30)
        );
        return sessionId;
    }
}
```

#### 4.5 测试验证阶段

**测试脚本**: `test_streamable_comprehensive.sh`

```bash
#!/bin/bash
# Streamable 协议完整测试套件
#
# @traceability
#   - Requirement: REQ-001 Streamable Protocol Support
#   - Design: ADR-001
#   - Code: McpRouterServerConfig.java, McpSessionService.java
#   - Documentation: docs/features/streamable-session-management.md

# A. Streamable 协议完整性测试
test_streamable_session_message() {
    print_test "验证第一条 NDJSON 消息包含 session 信息"
    
    FIRST_LINE=$(curl -s -N -H "Accept: application/x-ndjson" \
        "$ROUTER_URL/mcp/$SERVICE_NAME" | head -n 1)
    
    # 验证字段
    TYPE=$(echo "$FIRST_LINE" | jq -r '.type')
    SESSION_ID=$(echo "$FIRST_LINE" | jq -r '.sessionId')
    
    if [[ "$TYPE" == "session" ]] && [[ -n "$SESSION_ID" ]]; then
        print_pass "Session 消息格式正确"
    else
        print_fail "Session 消息字段不完整"
    fi
}

# C. Session 生命周期测试
test_session_lifecycle() {
    print_test "验证 Session 存储在 Redis"
    
    SESSION_ID=$(...)
    REDIS_KEY="mcp:session:$SESSION_ID"
    
    if redis-cli EXISTS "$REDIS_KEY" | grep -q "1"; then
        print_pass "Session 已存储到 Redis"
    else
        print_fail "Session 未在 Redis 中找到"
    fi
}
```

**GitHub Actions**: `.github/workflows/test-streamable-session.yml`

```yaml
# Streamable Session 管理测试
#
# @traceability
#   - Requirement: REQ-001
#   - Design: ADR-001
#   - Code: McpRouterServerConfig.java

name: Streamable Session Management Tests

on:
  pull_request:
    paths:
      - 'mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java'
      - 'mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpSessionService.java'

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      redis:
        image: redis:7-alpine
    steps:
      - name: Run comprehensive tests
        run: ./test_streamable_comprehensive.sh
```

#### 4.6 文档编写阶段

**功能文档**: `docs/features/streamable-session-management.md`

```markdown
# Streamable Session Management

> 实现 MCP 2024-11-05 Streamable 协议的 Session 管理

## Traceability
- **Requirement**: REQ-001 Streamable Protocol Support
- **Design**: [ADR-001](../adr/001-streamable-session-dual-transmission.md)
- **Code**: 
  - `McpRouterServerConfig.java`
  - `McpSessionService.java`
- **Test**: `test_streamable_comprehensive.sh`

## 概述
本功能实现了符合 MCP 2024-11-05 标准的 Streamable 协议...

## 架构设计
详见 [ADR-001](../adr/001-streamable-session-dual-transmission.md)

## 使用方法
...

## 测试验证
参见测试脚本: `test_streamable_comprehensive.sh`
```

**API 文档**: `docs/reference/api/streamable-endpoints.md`

```markdown
# Streamable 端点参考

## GET /mcp/{serviceName}

建立 Streamable 连接

### 响应

第一条消息（NDJSON 格式）:
```json
{
  "type": "session",
  "sessionId": "uuid",
  "messageEndpoint": "/mcp/message",
  "transport": "streamable"
}
```

### 追溯
- **Requirement**: REQ-001
- **Implementation**: McpRouterServerConfig.handleStreamableConnection()
- **Test**: test_streamable_comprehensive.sh (Section A)
```

---

### 案例 2: 虚拟项目功能

#### 完整追溯链

```
需求层 (Requirements)
├─ REQ-002: Virtual Project Support
├─ 描述: 支持将多个Dubbo服务组合为虚拟项目
└─ 位置: GitHub Issue #XX

        ↓

设计层 (Design)
├─ ADR-002: Virtual Project Architecture
├─ 架构图: 虚拟项目编排机制
├─ 数据库设计: zk_project, zk_virtual_project_endpoint
└─ 位置: docs/adr/002-virtual-project.md

        ↓

实现层 (Implementation)
├─ VirtualProjectService.java
├─ VirtualProjectController.java
├─ NacosMcpRegistrationService.java (注册虚拟项目)
└─ 位置: zk-mcp-parent/zkInfo/src/main/java/

        ↓

测试层 (Testing)
├─ 单元测试: VirtualProjectServiceTest.java
├─ 集成测试: /api/virtual-projects API测试
├─ 端到端测试: verify-core-capabilities.sh
└─ 位置: src/test/, testScript/

        ↓

文档层 (Documentation)
├─ 用户指南: docs/how-to-guides/create-virtual-project.md
├─ API参考: docs/reference/api/virtual-projects.md
└─ 故障排除: docs/how-to-guides/troubleshooting.md#虚拟项目
```

---

## 5. 工具和自动化

### 5.1 追溯标记规范

在代码中使用 JavaDoc 标记追溯信息：

```java
/**
 * Service for managing virtual projects
 * 
 * @traceability
 *   - Requirement: REQ-002 Virtual Project Support
 *   - Design: ADR-002 Virtual Project Architecture
 *   - Design: docs/explanations/architecture.md#虚拟项目
 *   - Test: VirtualProjectServiceTest.java
 *   - Test: testScript/verify-core-capabilities.sh
 *   - API Documentation: docs/reference/api/virtual-projects.md
 *   - User Guide: docs/how-to-guides/create-virtual-project.md
 * 
 * @author ZkInfo Team
 * @since 1.0.0
 */
@Service
public class VirtualProjectService {
    // ...
}
```

### 5.2 追溯矩阵

创建追溯矩阵文档: `docs/traceability/traceability-matrix.md`

```markdown
# 追溯矩阵 (Traceability Matrix)

| Requirement | Design | Code | Test | Documentation |
|-------------|--------|------|------|---------------|
| REQ-001 Streamable | [ADR-001](../adr/001-streamable-session.md) | `McpRouterServerConfig.java`<br>`McpSessionService.java` | `test_streamable_comprehensive.sh`<br>`.github/workflows/test-streamable-session.yml` | [Streamable Session Management](../features/streamable-session-management.md) |
| REQ-002 Virtual Project | [ADR-002](../adr/002-virtual-project.md) | `VirtualProjectService.java`<br>`VirtualProjectController.java` | `VirtualProjectServiceTest.java`<br>`verify-core-capabilities.sh` | [Create Virtual Project](../how-to-guides/create-virtual-project.md) |
| REQ-003 Dubbo泛化调用 | [架构设计](../explanations/architecture.md#dubbo泛化调用) | `McpExecutorService.java` | `McpExecutorServiceTest.java` | [Dubbo Integration](../reference/dubbo-integration.md) |
```

### 5.3 自动化工具

#### 工具 1: 追溯链检查脚本

```bash
#!/bin/bash
# scripts/maintenance/check-traceability.sh
# 检查每个需求是否有完整的追溯链

echo "检查追溯完整性..."

# 1. 提取所有需求ID
requirements=$(grep -r "REQ-" docs/ | grep -oP 'REQ-\d+' | sort -u)

for req in $requirements; do
    echo "检查 $req..."
    
    # 检查设计文档
    if ! grep -r "$req" docs/adr/ docs/explanations/; then
        echo "  ❌ 缺少设计文档"
    fi
    
    # 检查代码实现
    if ! grep -r "@traceability.*$req" src/; then
        echo "  ❌ 缺少代码实现"
    fi
    
    # 检查测试
    if ! grep -r "$req" src/test/ testScript/; then
        echo "  ❌ 缺少测试"
    fi
    
    # 检查文档
    if ! grep -r "$req" docs/; then
        echo "  ❌ 缺少用户文档"
    fi
done
```

#### 工具 2: 文档引用检查

```python
#!/usr/bin/env python3
# scripts/maintenance/check-doc-references.py
# 检查文档中的内部链接是否有效

import os
import re
from pathlib import Path

def find_broken_links(docs_dir):
    """查找损坏的内部链接"""
    broken_links = []
    
    for md_file in Path(docs_dir).rglob('*.md'):
        content = md_file.read_text()
        
        # 查找所有内部链接
        links = re.findall(r'\[.*?\]\((.*?\.md.*?)\)', content)
        
        for link in links:
            # 解析相对路径
            target = (md_file.parent / link).resolve()
            
            if not target.exists():
                broken_links.append({
                    'file': str(md_file),
                    'link': link,
                    'target': str(target)
                })
    
    return broken_links

if __name__ == '__main__':
    broken = find_broken_links('docs/')
    
    if broken:
        print(f"Found {len(broken)} broken links:")
        for item in broken:
            print(f"  File: {item['file']}")
            print(f"  Link: {item['link']}")
            print(f"  Target: {item['target']}")
            print()
    else:
        print("✅ All internal links are valid!")
```

#### 工具 3: GitHub Actions 集成

```yaml
# .github/workflows/traceability-check.yml
name: Traceability Check

on:
  pull_request:
    paths:
      - 'src/**'
      - 'docs/**'

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Check traceability completeness
        run: |
          chmod +x scripts/maintenance/check-traceability.sh
          ./scripts/maintenance/check-traceability.sh
      
      - name: Check documentation links
        run: |
          python3 scripts/maintenance/check-doc-references.py
      
      - name: Comment PR if issues found
        if: failure()
        uses: actions/github-script@v7
        with:
          script: |
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '⚠️ 追溯性检查发现问题，请检查CI日志'
            });
```

---

## 6. 最佳实践

### 6.1 需求管理最佳实践

#### ✅ DO（推荐做法）

1. **每个需求都有唯一ID**
   ```
   REQ-001: Streamable Protocol Support
   REQ-002: Virtual Project Support
   ```

2. **需求包含验收标准**
   ```markdown
   ## 验收标准
   - [ ] 第一条消息包含 session 信息
   - [ ] 支持多种 Session ID 传递方式
   - [ ] Session 存储在 Redis
   ```

3. **需求关联用户故事**
   ```markdown
   ## 用户故事
   作为 AI 客户端开发者
   我希望能够使用 Streamable 协议连接 MCP Server
   以便符合最新的 MCP 2024-11-05 标准
   ```

#### ❌ DON'T（避免做法）

- ❌ 需求描述模糊，没有明确的验收标准
- ❌ 需求没有唯一标识符
- ❌ 需求变更后不更新追溯链

### 6.2 设计文档最佳实践

#### ✅ DO

1. **使用 ADR 记录架构决策**
   ```markdown
   # ADR-001: Streamable 协议双重 Session ID 传递机制
   
   ## Status
   ✅ Accepted
   
   ## Context
   (问题和背景)
   
   ## Decision
   (决策内容)
   
   ## Alternatives Considered
   (考虑过的替代方案)
   
   ## Consequences
   (后果和权衡)
   ```

2. **设计文档包含追溯信息**
   ```markdown
   ## Traceability
   - Requirement: REQ-001
   - Implementation: McpRouterServerConfig.java
   - Test: test_streamable_comprehensive.sh
   - Documentation: docs/features/streamable-session-management.md
   ```

3. **设计变更时更新文档**
   - 在 PR 中同时更新设计文档
   - 标记已弃用的设计（状态改为 Deprecated）

#### ❌ DON'T

- ❌ 设计文档和代码实现不同步
- ❌ 没有记录设计决策的原因
- ❌ 缺少替代方案的对比分析

### 6.3 代码实现最佳实践

#### ✅ DO

1. **在代码中标记追溯信息**
   ```java
   /**
    * @traceability
    *   - Requirement: REQ-001
    *   - Design: ADR-001
    *   - Test: test_streamable_comprehensive.sh
    */
   ```

2. **代码变更时同步更新文档**
   - PR Checklist 包含"更新相关文档"
   - CI 检查文档是否需要更新

3. **重要逻辑添加注释说明设计意图**
   ```java
   // ADR-001: 采用双重传递机制
   // 1. 响应体中的 session 消息
   // 2. 响应头中的 Mcp-Session-Id
   ```

#### ❌ DON'T

- ❌ 代码中没有任何追溯信息
- ❌ 修改代码但不更新设计文档
- ❌ 注释和实际代码不一致

### 6.4 测试验证最佳实践

#### ✅ DO

1. **测试脚本包含追溯信息**
   ```bash
   # @traceability
   #   - Requirement: REQ-001
   #   - Design: ADR-001
   #   - Code: McpRouterServerConfig.java
   ```

2. **测试用例对应验收标准**
   ```bash
   # 验收标准 1: 第一条消息包含 session 信息
   test_streamable_session_message() {
       # ...
   }
   
   # 验收标准 2: 支持多种 Session ID 传递方式
   test_session_id_headers() {
       # ...
   }
   ```

3. **测试失败时易于定位问题**
   ```bash
   if [[ $test_failed ]]; then
       echo "❌ Test failed: Session message format"
       echo "Requirement: REQ-001"
       echo "Expected: {type: 'session', sessionId: '...'}"
       echo "Actual: $response"
   fi
   ```

#### ❌ DON'T

- ❌ 测试与需求脱节
- ❌ 测试覆盖不完整
- ❌ 测试失败信息不明确

### 6.5 文档编写最佳实践

#### ✅ DO

1. **文档包含完整的追溯链**
   ```markdown
   ## Traceability
   - Requirement: REQ-001
   - Design: ADR-001
   - Code: (链接到代码)
   - Test: (链接到测试)
   ```

2. **使用 Frontmatter 管理元数据**
   ```markdown
   ---
   status: active
   created: 2026-01-28
   last_updated: 2026-02-11
   review_date: 2026-05-11
   requirements: [REQ-001, REQ-002]
   ---
   ```

3. **文档分类清晰**
   - Tutorial: 教学性质
   - How-To: 任务导向
   - Explanation: 理解导向
   - Reference: 信息导向

#### ❌ DON'T

- ❌ 文档没有追溯信息
- ❌ 文档过时但没有标记
- ❌ 文档分类混乱

---

## 7. 流程串联实践

### 7.1 新功能开发完整流程

```
步骤 1: 需求分析
├─ 创建 GitHub Issue
├─ 分配 REQ ID
├─ 编写用户故事和验收标准
└─ 评审需求

步骤 2: 架构设计
├─ 创建 ADR (如需要)
├─ 更新架构文档
├─ 设计 API 接口
├─ 设计数据库表
└─ 评审设计

步骤 3: 实现计划
├─ 创建功能分支 feature/REQ-XXX
├─ 编写实现计划
└─ 分解任务

步骤 4: 编码实现
├─ 编写代码
├─ 添加 @traceability 注释
├─ 编写单元测试
└─ 本地验证

步骤 5: 测试验证
├─ 运行单元测试
├─ 运行集成测试
├─ 编写端到端测试脚本
└─ 更新 CI/CD

步骤 6: 文档编写
├─ 编写功能文档 (docs/features/)
├─ 更新 API 文档 (docs/reference/)
├─ 编写操作指南 (docs/how-to-guides/)
└─ 更新 README

步骤 7: 代码审查
├─ 创建 Pull Request
├─ 添加追溯矩阵
├─ 自动化检查通过
├─ 团队审查
└─ 修改反馈

步骤 8: 合并和发布
├─ 合并到主分支
├─ 更新 CHANGELOG
├─ 创建 Release Tag
└─ 部署到环境

步骤 9: 验收和关闭
├─ 验收测试
├─ 用户验收
├─ 关闭 Issue
└─ 归档文档
```

### 7.2 Bug 修复流程

```
步骤 1: Bug 报告
├─ 创建 GitHub Issue (BUG-XXX)
├─ 描述现象
├─ 提供复现步骤
└─ 标记严重程度

步骤 2: Root Cause Analysis
├─ 追溯到相关需求 (REQ-XXX)
├─ 查看设计文档 (ADR-XXX)
├─ 定位代码位置
├─ 分析根本原因
└─ 评估影响范围

步骤 3: 修复实现
├─ 创建修复分支 bugfix/BUG-XXX
├─ 修改代码
├─ 添加回归测试
└─ 本地验证

步骤 4: 文档更新
├─ 更新相关文档 (如需要)
├─ 添加到故障排除指南
└─ 更新 CHANGELOG

步骤 5: 审查和发布
├─ 创建 PR
├─ 代码审查
├─ 合并
└─ 发布补丁版本
```

### 7.3 文档维护流程

```
每月第一周: 文档健康检查
├─ 运行 check-traceability.sh
├─ 运行 check-doc-references.py
├─ 检查过期文档 (review_date)
└─ 生成维护报告

每季度: 文档审查
├─ 审查所有 active 文档
├─ 更新过时信息
├─ 归档不再使用的文档
└─ 补充缺失文档

每次发版: 文档同步
├─ 更新版本号
├─ 更新 CHANGELOG
├─ 更新 API 文档
└─ 发布文档站点
```

---

## 附录

### A.1 文档模板

#### ADR 模板

```markdown
# ADR-XXX: [标题]

## Status
[Proposed/Accepted/Deprecated/Rejected/Superseded]

## Context
描述问题和背景

## Decision
描述决策内容

## Alternatives Considered
1. 方案A: ...
2. 方案B: ...

## Consequences
### Positive
- 优点1
- 优点2

### Negative
- 缺点1
- 缺点2

## Traceability
- Requirement: REQ-XXX
- Implementation: (代码文件)
- Test: (测试文件)
- Documentation: (文档链接)

## References
- (相关资料)
```

#### 功能文档模板

```markdown
---
status: active
created: YYYY-MM-DD
last_updated: YYYY-MM-DD
review_date: YYYY-MM-DD
requirements: [REQ-XXX]
tags: [feature, mcp, streamable]
---

# [功能名称]

> 简短描述

## Traceability
- **Requirement**: REQ-XXX
- **Design**: [ADR-XXX](../adr/XXX.md)
- **Code**: 
  - `File1.java`
  - `File2.java`
- **Test**: 
  - `TestFile.java`
  - `test_script.sh`

## 概述
(功能概述)

## 架构设计
(架构图、时序图)

## 使用方法
(示例代码)

## 配置
(配置说明)

## 测试验证
(如何测试)

## 故障排除
(常见问题)

## 参考资料
(相关链接)
```

### A.2 追溯标记清单

在以下位置添加追溯标记：

- [x] **需求文档**: GitHub Issues, 需求文档
- [x] **设计文档**: ADR, 架构文档
- [x] **代码**: JavaDoc `@traceability`
- [x] **测试**: 测试脚本注释
- [x] **CI/CD**: workflow 注释
- [x] **用户文档**: Frontmatter + Traceability 章节

### A.3 检查清单

#### PR 提交前检查

- [ ] 代码包含 `@traceability` 标记
- [ ] 相关设计文档已更新
- [ ] 测试覆盖新功能
- [ ] 用户文档已更新
- [ ] 追溯矩阵已更新
- [ ] 所有链接有效
- [ ] CI/CD 通过

#### 文档发布前检查

- [ ] 所有文档有 frontmatter
- [ ] `review_date` 已设置
- [ ] 内部链接有效
- [ ] 代码示例可运行
- [ ] 图片和图表清晰
- [ ] 拼写和语法正确

---

**文档版本**: 1.0  
**创建时间**: 2026-02-11  
**下次审查**: 2026-05-11  
**维护者**: Documentation Team  
**相关文档**: 
- [SYSTEM_ARCHITECTURE_ANALYSIS.md](./SYSTEM_ARCHITECTURE_ANALYSIS.md)
- [CICD_PROCESS_ANALYSIS.md](./CICD_PROCESS_ANALYSIS.md)
- [PROJECT_ANALYSIS.md](./PROJECT_ANALYSIS.md)
