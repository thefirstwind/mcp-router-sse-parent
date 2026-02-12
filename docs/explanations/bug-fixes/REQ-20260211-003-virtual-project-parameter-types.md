# REQ-20260211-003: 虚拟项目参数类型修复

## 背景
在高频调用虚拟节点服务时，发现部分方法因为参数类型推断不准确（如 `int` vs `Long`）导致 Dubbo 调用抛出 `NoSuchMethodException`。

## 解决方案
### 1. 显式参数类型定义
在虚拟项目的 `sN.json` 配置文件中增加了 `parameterTypes` 字段。该字段是一个字符串数组，通过 Java 类型全限定名（如 `java.lang.Long`）明确指定方法签名。

### 2. 动态元数据补全
在 `McpProtocolService` 的工具发现逻辑中，实现了本地配置与实时上报元数据的合并。即便 Provider 未在线上报元数据，系统也能从本地文件中读取完整的参数类型定义。

### 3. 类型自动转换
在执行工具调用前，系统会根据显式定义的参数类型，自动对传入的 JSON 参数（如 `Integer`）进行转换（如 `longValue()`），确保与 Dubbo 方法签名完全一致。

## 追溯链
- Requirement: REQ-20260211-003
- Code: `McpProtocolService.java`
- Configuration: `s14.json`
