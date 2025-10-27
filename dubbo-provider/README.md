# Dubbo2 Provider + Dubbo2 Consumer 示例项目

这是一个完整的 Dubbo2 示例项目，包含 Provider 和 Consumer 两个模块，使用 Zookeeper 作为注册中心。

## 技术栈

- **Provider**: Java 8, Dubbo 2.7.21, Spring Boot 1.5.22.RELEASE
- **Consumer**: Java 17, Dubbo 2.7.3, Spring Boot 2.7.18
- **注册中心**: Zookeeper 3.4.5
- **序列化**: Hessian2
- **构建工具**: Maven

## 项目结构

```
zookeeper-0802/
├── api/                    # 共享 API 模块
├── provider/              # Dubbo2 Provider
├── consumer/              # Dubbo2 Consumer
├── zookeeper/            # Zookeeper 安装和配置
├── start-all.sh          # 启动所有服务
├── start-consumer.sh     # 启动 Consumer
├── test-api.sh           # API 测试脚本
└── README.md             # 项目说明
```

## 快速开始

### 1. 启动 Zookeeper

```bash
# 安装 Zookeeper
./zookeeper/install-zookeeper.sh

# 启动 Zookeeper
./zookeeper/start-zookeeper.sh

# 检查 Zookeeper 状态
./zookeeper/check-zookeeper.sh
```

### 2. 启动 Provider

```bash
# 编译并启动 Provider
cd dubbo-provider
mvn clean package -DskipTests
export JAVA_HOME=/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
java -jar target/dubbo-provider-1.0.0.jar
```

### 3. 启动 Consumer

```bash
# 使用启动脚本（推荐）
./start-consumer.sh

# 或者手动启动
cd consumer
mvn clean package -DskipTests
java \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens java.base/java.io=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/java.nio.channels=ALL-UNNAMED \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.http=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED \
    --add-opens java.base/java.math=ALL-UNNAMED \
    --add-opens java.base/java.text=ALL-UNNAMED \
    --add-opens java.base/java.time=ALL-UNNAMED \
    --add-opens java.base/java.time.chrono=ALL-UNNAMED \
    --add-opens java.base/java.time.format=ALL-UNNAMED \
    --add-opens java.base/java.time.temporal=ALL-UNNAMED \
    --add-opens java.base/java.time.zone=ALL-UNNAMED \
    -Djava.security.manager=allow \
    -Djava.net.preferIPv4Stack=true \
    -Dzookeeper.sasl.client=false \
    -jar target/dubbo-consumer-1.0.0.jar
```

```bash

cd consumer2
mvn clean package -DskipTests
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.nio.channels=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/sun.net.www.protocol.http=ALL-UNNAMED --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.time.chrono=ALL-UNNAMED --add-opens java.base/java.time.format=ALL-UNNAMED --add-opens java.base/java.time.temporal=ALL-UNNAMED --add-opens java.base/java.time.zone=ALL-UNNAMED -Djava.security.manager=allow -Djava.net.preferIPv4Stack=true -Dzookeeper.sasl.client=false -jar target/dubbo-consumer-1.0.0.jar 
```

### 4. 测试 API

```bash
# 运行完整测试
./test-api.sh

# 或者单独测试
curl http://localhost:18081/api/health
curl http://localhost:18081/api/users
curl http://localhost:18081/api/users/1
```

## API 接口

### Consumer 接口 (端口: 18081)

- `GET /api/health` - 健康检查
- `GET /api/users` - 获取所有用户
- `GET /api/users/{id}` - 根据ID获取用户
- `POST /api/users` - 创建用户
- `PUT /api/users/{id}` - 更新用户
- `DELETE /api/users/{id}` - 删除用户
- `GET /api/users/search?username={username}` - 搜索用户

### Provider 接口 (端口: 20881)

- Dubbo 服务接口，通过 Consumer 调用

## 配置说明

### Provider 配置

- **端口**: 20881 (Spring Boot), 20880 (Dubbo)
- **注册中心**: zookeeper://127.0.0.1:2181
- **序列化**: hessian2

### Consumer 配置

- **端口**: 18081 (Spring Boot)
- **注册中心**: zookeeper://127.0.0.1:2181
- **序列化**: hessian2
- **JVM 参数**: 包含 Java 17 模块系统兼容性参数

## 关键特性

1. **版本兼容性**: Dubbo2 Provider 和 Consumer 完全兼容
2. **Java 版本**: Provider 使用 Java 8，Consumer 使用 Java 17
3. **Spring Boot 版本**: Provider 使用 1.5.x，Consumer 使用 2.7.x
4. **模块化配置**: 使用 ReferenceBean 方式注册服务
5. **完整的 CRUD 操作**: 支持用户的增删改查
6. **错误处理**: 完善的异常处理和日志记录

## 注意事项

1. **Java 版本要求**: 确保系统安装了 Java 8 和 Java 17
2. **端口占用**: 确保 2181 (Zookeeper)、20880/20881 (Provider)、18081 (Consumer) 端口未被占用
3. **JVM 参数**: Consumer 需要特定的 JVM 参数来支持 Java 17 模块系统
4. **网络配置**: 使用 127.0.0.1 而不是 localhost 来避免 IPv6 问题

## 故障排除

### 常见问题

1. **Consumer 启动失败**: 检查 JVM 参数是否完整
2. **连接超时**: 确保 Zookeeper 正常运行
3. **序列化错误**: 检查 Java 版本兼容性
4. **端口冲突**: 检查端口是否被占用

### 日志查看

- Provider 日志: 查看 Provider 控制台输出
- Consumer 日志: 查看 Consumer 控制台输出
- Zookeeper 日志: 查看 `zookeeper/logs/` 目录

## 开发说明

### 添加新的服务接口

1. 在 `api` 模块中定义接口
2. 在 `provider` 模块中实现接口
3. 在 `consumer` 模块中创建对应的 REST 接口

### 修改配置

- Provider 配置: `provider/src/main/resources/application.yml`
- Consumer 配置: `consumer/src/main/resources/application.yml`
- Dubbo 配置: `consumer/src/main/java/com/example/consumer/config/DubboConfig.java`

## 许可证

MIT License 