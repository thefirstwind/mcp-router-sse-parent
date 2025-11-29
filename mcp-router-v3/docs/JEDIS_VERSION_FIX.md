# Jedis 版本修复说明

## 问题

编译时出现错误：
```
[ERROR] Could not find artifact redis.clients:jedis:jar:2.1.1 in aliyunmaven
```

## 原因

Jedis 2.1.1 版本在 Maven Central 和阿里云 Maven 仓库中都不存在。这是一个非常老的版本（2013年），可能已经从公共仓库中移除了。

## 解决方案

### 1. 使用 Jedis 2.9.0（已修复）

已将 Jedis 版本从 2.1.1 更新为 2.9.0，并修复了 API 兼容性问题：

- `hsetAll`: 使用 `hmset` 方法替代 `hset(key, hash)`
- `expire`: 将 `long` 参数转换为 `int`（Jedis 2.9.0 的 expire 方法接受 int）

### 2. 代码修改

**LocalJedisRedisClient.java**:
- `hsetAll`: 改为使用 `jedis.hmset(key, hash)`
- `expire`: 改为使用 `jedis.expire(key, (int) seconds)`

## 生产环境注意事项

如果生产环境**必须**使用 Jedis 2.1.1，有以下选项：

### 选项 1: 使用内部 Maven 仓库

如果公司有内部 Maven 仓库包含 Jedis 2.1.1，可以在 `pom.xml` 中添加仓库配置：

```xml
<repositories>
    <repository>
        <id>internal-repo</id>
        <url>http://your-internal-repo/maven</url>
    </repository>
</repositories>
```

### 选项 2: 手动安装到本地仓库

```bash
mvn install:install-file \
  -Dfile=jedis-2.1.1.jar \
  -DgroupId=redis.clients \
  -DartifactId=jedis \
  -Dversion=2.1.1 \
  -Dpackaging=jar
```

### 选项 3: 使用 Jedis 2.9.0（推荐）

Jedis 2.9.0 与 2.1.1 API 兼容，代码已修复以支持 2.9.0。如果生产环境可以接受，建议使用 2.9.0。

## 验证

编译已通过：
```bash
mvn clean compile -DskipTests
```

## 相关文件

- `pom.xml`: Jedis 版本已更新为 2.9.0
- `LocalJedisRedisClient.java`: API 调用已修复以兼容 2.9.0





