# REQ-20260211-003 解决方案总结

**创建时间**: 2026-02-11 20:35  
**状态**: 部分完成，需要手动更新虚拟项目配置

## 📋 问题回顾

虚拟项目调用Dubbo服务时出现 `NoSuchMethodException: getUserById(int)` 错误，期望应该是 `getUserById(Long)`。

**根本原因**: 虚拟项目的工具定义JSON配置中缺少Java参数类型信息，导致Dubbo泛化调用时使用了错误的参数类型。

---

## ✅ 已完成的修改

### 1. 数据模型层
- ✅ `McpResponse.McpTool`: 添加了 `parameterTypes` 字段
- ✅ `McpProtocol.McpTool`: 添加了 `parameterTypes` 字段

### 2. 工具生成层（适用于真实Dubbo服务）
- ✅ `McpToolSchemaGenerator.getParameterTypes()`: 新增方法，从数据库/ZK元数据获取参数类型
- ✅ `McpConverterService.convertProvidersToTools()`: 生成工具时自动填充`parameterTypes`

### 3. 工具调用层
- ✅ `McpProtocolService.extractParameterTypes()`: 优先使用工具定义中的`parameterTypes`字段
- ✅ `McpProtocolService.convertToMcpTool()`: 转换时保留`parameterTypes`字段

---

## ⚠️ 待处理的部分：虚拟项目配置

### 问题现状

目前虚拟项目的JSON配置文件（如`virtual-projects/s13.json`）的格式为：

```json
{
  "tools": [
    {
      "interfaceName": "com.pajk.provider3.service.UserService",
      "methodName": "getUserById",
      "toolName": "com.pajk.provider3.service.UserService.getUserById",
      "description": "查询 User By Id (UserService)",
      "inputSchema": "{...JSON字符串...}"
      ⚠️ 缺少 "parameterTypes" 字段
    }
  ]
}
```

### 解决方案

需要在虚拟项目配置中添加 `parameterTypes` 字段。有两种方式：

#### 方案A: 手动编辑现有JSON文件（临时方案） ✋

手动编辑 `virtual-projects/s13.json`，为每个工具添加 `parameterTypes` 字段：

```json
{
  "tools": [
    {
      "interfaceName": "com.pajk.provider3.service.UserService",
      "methodName": "getUserById",
      "toolName": "com.pajk.provider3.service.UserService.getUserById",
      "description": "查询 User By Id (UserService)",
      "inputSchema": "{...}",
      "parameterTypes": ["java.lang.Long"]  ⬅️ 添加这一行
    }
  ]
}
```

#### 方案B: 修改向导代码自动生成（长期方案） 🔧

需要修改以下代码：

1. **`VirtualProjectWizardController.java`** 或 **相关服务类**：
   - 在生成工具定义时，调用`McpToolSchemaGenerator.getParameterTypes()`
   - 将参数类型添加到工具的Map中

2. **示例修改位置**（具体代码需要定位）:
   ```java
   // 生成工具定义时
   Map<String, Object> tool = new HashMap<>();
   tool.put("toolName", toolName);
   tool.put("description", description);
   tool.put("inputSchema", inputSchemaJson);
   
   // ✅ 添加以下代码
   List<String> parameterTypes = mcpToolSchemaGenerator.getParameterTypes(interfaceName, methodName);
   if (parameterTypes != null && !parameterTypes.isEmpty()) {
       tool.put("parameterTypes", parameterTypes);
   }
   ```

3. **重新生成所有虚拟项目配置**

---

## 🧪 验证步骤

### 方案A（手动修改）验证：

1. 编辑 `virtual-projects/s13.json`，添加 `parameterTypes`
2. 重启 zkInfo 服务：
   ```bash
   cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo
   pkill -9 -f "jar.*zkInfo"
   nohup java -jar target/zk-monitor-1.0.0.jar > logs/zkInfo.log 2>&1 &
   ```
3. 通过MCP Inspector调用 `getUserById` 方法
4. 查看日志，应该显示：
   ```
   ✅ 使用工具定义中的 parameterTypes: com.pajk.provider3.service.UserService.getUserById -> [java.lang.Long]
   ```

### 方案B（代码修改）验证：

1. 修改代码后重新编译
2. 通过向导重新创建虚拟项目
3. 检查生成的JSON文件是否包含`parameterTypes`
4. 测试调用

---

## 📝 需要用户决策

**请选择下面的方案之一：**

### 选项1: 我手动修改现有JSON文件（快速验证）
- 优点：立即可以测试
- 缺点：以后创建新虚拟项目仍然需要手动添加

### 选项2: 我想修改代码让向导自动生成
- 优点：一劳永逸
- 缺点：需要定位并修改向导代码，重新生成所有虚拟项目

### 选项3: 我两个都做
- 先手动修改验证可行性
- 然后修改代码实现自动化

---

## 🎯 推荐方案

**推荐选项3**：
1. **立即**: 手动编辑 `s13.json` 添加 `parameterTypes: ["java.lang.Long"]`
2. **验证**: 重启服务测试调用是否成功
3. **长期**: 如果验证成功，再修改向导代码实现自动生成

---

## 📦 相关文件

- `/Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo/virtual-projects/s13.json` - 需要手动编辑
- `/Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/controller/VirtualProjectWizardController.java` - 向导控制器
- `/Users/shine/projects.mcp-router-sse-parent/docs/requirements/REQ-20260211-003.md` - 原始需求文档
