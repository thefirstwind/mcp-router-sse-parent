# 虚拟节点参数名问题解决总结

> **日期**: 2026-02-11  
> **需求**: REQ-20260211-002  
> **状态**: ✅ 已解决并验证

---

## 🎯 问题描述

**现象**: 虚拟节点注册时，参数名显示为 `arg0`, `arg1` 等默认名称，而非真实的参数名（如 `userId`）

**影响**: 用户必须查看源码才知道应该传什么参数，极大影响可用性

---

## 🔍 根本原因

### 原因1: ASM扫描时跳过了调试信息（关键Bug）

**文件**: `JarScannerService.java` Line 98

```java
// ❌ 错误代码
classReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
```

**问题**: `ClassReader.SKIP_DEBUG` 标志会跳过所有调试信息，包括Java的 **MethodParameters** 属性，这正是参数名存储的地方！

### 原因2: demo-provider3未启用参数名编译

**文件**: `demo-provider3/pom.xml`

**问题**: 缺少 Maven Compiler Plugin 的 `-parameters` 配置，导致编译后的class文件不包含参数名

---

## ✅ 解决方案

### 修复1: 移除SKIP_DEBUG标志

**文件**: `/zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/JarScannerService.java`

```java
// ✅ 正确代码
classReader.accept(visitor, 0);  // 读取所有信息包括参数名
```

**说明**: 使用 `0` 作为标志，让ASM读取所有信息，包括MethodParameters属性

### 修复2: 增强ASM Visitor支持参数名读取

**文件**: 同上

```java
@Override
public void visitParameter(String paramName, int paramAccess) {
    // 从 Parameter 注解或 -parameters 编译选项获取参数名
    if (paramName != null && !paramName.isEmpty()) {
        parameterNames.add(paramName);
    }
}
```

**说明**: 实现visitParameter方法来捕获参数名

### 修复3: 添加-parameters编译选项

**文件**: `/zk-mcp-parent/demo-provider3/pom.xml`

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
        <parameters>true</parameters>  <!-- 关键配置 -->
    </configuration>
</plugin>
```

**说明**: 告诉Java编译器在class文件中保留参数名信息

### 修复4: 版本升级

- demo-provider3: `1.0.3` → `1.0.4`
- 重新编译并安装到本地Maven仓库

---

## 📋 验证步骤

1. ✅ 修改 `JarScannerService.java` - 移除SKIP_DEBUG
2. ✅ 修改 `demo-provider3/pom.xml` - 添加-parameters
3. ✅ 重新编译 demo-provider3 (版本1.0.4)
4. ✅ 验证JAR包包含参数名:
   ```bash
   javap -v UserService.class | grep -A 5 getUserById
   # 输出: MethodParameters:
   #        Name                           Flags
   #        userId
   ```
5. ✅ 重新编译并重启 zkInfo
6. ✅ 在管理界面提交依赖（1.0.4版本）
7. ✅ 创建新虚拟节点
8. ✅ 验证参数名为 `userId` 而不是 `arg0`

---

## 🎓 知识点总结

### 1. Java参数名存储机制

- **编译选项**: `-parameters` (Java 8+)
- **存储位置**: class文件的 **MethodParameters** 属性
- **类型**: 调试信息的一部分
- **默认行为**: 不保留（编译器优化）

### 2. ASM读取参数名的方法

**方法1**: `visitParameter()` - 读取MethodParameters属性
- 优点: 最准确，无论是接口还是实现类都能读取
- 缺点: 需要源代码使用`-parameters`编译

**方法2**: `visitLocalVariable()` - 读取LocalVariableTable
- 优点: 不需要特殊编译选项
- 缺点: **接口方法没有LocalVariableTable**（因为是抽象方法）

### 3. ClassReader标志

- `0`: 读取所有信息（包括调试信息）
- `SKIP_DEBUG`: 跳过调试信息（**会跳过参数名！**）
- `SKIP_FRAMES`: 跳过栈帧信息（优化性能）

---

## 🚀 未来改进方向

基于用户建议，可以实施**三级降级策略**：

### 方案4: 从GitLab/GitHub源代码分析（推荐）

**架构文档**: `docs/adr/ADR-003-parameter-name-resolution.md`

**优势**:
1. ✅ 最可靠 - 源代码永远包含真实参数名
2. ✅ 不依赖编译选项 - 即使老项目也能工作
3. ✅ 额外信息 - 可以提取JavaDoc、参数说明等

**实施计划**:
```
1️⃣ 优先：从GitLab/GitHub源代码解析
   ↓ 失败
2️⃣ 降级：从class文件MethodParameters读取 (当前方案)
   ↓ 失败  
3️⃣ 兜底：使用默认参数名 arg0, arg1...
```

**所需组件**:
- JavaParser库 - 解析Java源代码
- GitApiClient - 从Git仓库获取源文件
- SourceCodeAnalyzerService - 协调源代码分析

---

## 📊 影响范围

### 修改的文件

| 文件 | 类型 | 修改内容 | 状态 |
|------|------|----------|------|
| `JarScannerService.java` | Code | 移除SKIP_DEBUG，增强visitParameter | ✅ |
| `demo-provider3/pom.xml` | Config | 添加-parameters编译选项 | ✅ |
| `ParameterConverter.java` | Code | 添加调试日志 | ✅ |
| `REQ-20260211-001.md` | Doc | Bug报告（误判） | ✅ |
| `REQ-20260211-002.md` | Doc | 真正的需求文档 | ✅ |
| `ADR-003-parameter-name-resolution.md` | Architecture | 架构设计文档 | ✅ |

### 需要部署的服务

- ✅ zkInfo - 重新编译并重启
- ✅ demo-provider3 - 版本升级到1.0.4

---

## ✨ 最终效果

### 修复前

```json
{
  "toolName": "com.pajk.provider3.service.UserService.getUserById",
  "inputSchema": {
    "properties": {
      "arg0": {  // ❌ 用户不知道这是什么
        "type": "integer",
        "description": "参数: arg0 (类型: Long)"
      }
    }
  }
}
```

### 修复后

```json
{
  "toolName": "com.pajk.provider3.service.UserService.getUserById",
  "inputSchema": {
    "properties": {
      "userId": {  // ✅ 清晰明了
        "type": "integer",
        "description": "参数: userId (类型: Long)"
      }
    }
  }
}
```

---

## 🔗 追溯链

- **Requirement**: docs/requirements/REQ-20260211-002.md
- **Design**: docs/adr/ADR-003-parameter-name-resolution.md
- **Code**: 
  - `JarScannerService.java` (关键修复)
  - `demo-provider3/pom.xml`
- **Test**: 手动验证通过
- **Documentation**: 本文档

---

## 📝 经验教训

1. **ASM标志很重要**: `SKIP_DEBUG`会跳过关键信息，需要谨慎使用
2. **编译选项影响运行时**: `-parameters`虽然增加少量文件大小，但对工具和框架非常重要
3. **接口vs实现类**: 接口方法没有LocalVariableTable，只能依赖MethodParameters
4. **调试方法**: `javap -v`是验证class文件内容的最佳工具

---

**创建时间**: 2026-02-11 20:06  
**验证状态**: ✅ 成功  
**相关需求**: REQ-20260211-002
