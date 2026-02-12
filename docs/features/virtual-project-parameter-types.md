# 虚拟项目参数类型支持 (REQ-20260211-003)

## 概述
为虚拟项目提供了显式的参数类型定义支持，以解决 Dubbo 泛化调用时的类型匹配问题。

## 配置方法
在虚拟项目的 `tools` 数组定义中，可以添加 `parameterTypes` 字段：

```json
{
  "tools": [
    {
      "toolName": "...",
      "interfaceName": "...",
      "methodName": "...",
      "parameterTypes": ["java.lang.Long"],
      "inputSchema": "..."
    }
  ]
}
```

## 自动转换
系统会自动将传入的 JSON 参数转换为定义的 Java 类型，目前支持：
- `Integer` -> `Long`
- `String` -> `Long`
- 基本类型自动拆装箱

## 追溯
- Requirement: REQ-20260211-003
- Design: docs/explanations/bug-fixes/REQ-20260211-003-virtual-project-parameter-types.md
