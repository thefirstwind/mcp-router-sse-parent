package com.example.dubbo.provider.service;

import com.example.dubbo.api.UserService;
import com.example.dubbo.model.User;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务实现类
 * 使用内存存储模拟数据库操作
 */
@DubboService(version = "1.0.0", timeout = 5000)
@Service
public class UserServiceImpl implements UserService {

    // 使用内存存储用户数据，模拟数据库
    private final Map<Long, User> userStorage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    // 初始化一些测试数据
    public UserServiceImpl() {
        initTestData();
    }

    private void initTestData() {
        User user1 = new User();
        user1.setId(idGenerator.getAndIncrement());
        user1.setUsername("admin");
        user1.setEmail("admin@example.com");
        user1.setRealName("管理员");
        user1.setPhone("13800138000");
        user1.setStatus(1);
        user1.setCreateTime(LocalDateTime.now());
        user1.setUpdateTime(LocalDateTime.now());
        userStorage.put(user1.getId(), user1);

        User user2 = new User();
        user2.setId(idGenerator.getAndIncrement());
        user2.setUsername("testuser");
        user2.setEmail("test@example.com");
        user2.setRealName("测试用户");
        user2.setPhone("13900139000");
        user2.setStatus(1);
        user2.setCreateTime(LocalDateTime.now());
        user2.setUpdateTime(LocalDateTime.now());
        userStorage.put(user2.getId(), user2);
    }

    @Override
    public User getUserById(Long userId) {
        System.out.println("Provider: 根据ID查询用户，ID=" + userId);
        User user = userStorage.get(userId);
        if (user != null) {
            System.out.println("Provider: 找到用户 " + user.getUsername());
        } else {
            System.out.println("Provider: 未找到用户，ID=" + userId);
        }
        return user;
    }

    @Override
    public List<User> getAllUsers() {
        System.out.println("Provider: 查询所有用户，总数=" + userStorage.size());
        return new ArrayList<>(userStorage.values());
    }

    @Override
    public Long createUser(User user) {
        System.out.println("Provider: 创建新用户 " + user.getUsername());
        Long userId = idGenerator.getAndIncrement();
        user.setId(userId);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userStorage.put(userId, user);
        System.out.println("Provider: 用户创建成功，ID=" + userId);
        return userId;
    }

    @Override
    public boolean updateUser(User user) {
        System.out.println("Provider: 更新用户信息，ID=" + user.getId());
        if (userStorage.containsKey(user.getId())) {
            user.setUpdateTime(LocalDateTime.now());
            userStorage.put(user.getId(), user);
            System.out.println("Provider: 用户更新成功");
            return true;
        } else {
            System.out.println("Provider: 用户不存在，更新失败，ID=" + user.getId());
            return false;
        }
    }

    @Override
    public boolean deleteUser(Long userId) {
        System.out.println("Provider: 删除用户，ID=" + userId);
        User removedUser = userStorage.remove(userId);
        if (removedUser != null) {
            System.out.println("Provider: 用户删除成功");
            return true;
        } else {
            System.out.println("Provider: 用户不存在，删除失败，ID=" + userId);
            return false;
        }
    }

    @Override
    public User getUserByUsername(String username) {
        System.out.println("Provider: 根据用户名查询用户，username=" + username);
        User foundUser = userStorage.values().stream()
                .filter(user -> username.equals(user.getUsername()))
                .findFirst()
                .orElse(null);
        
        if (foundUser != null) {
            System.out.println("Provider: 找到用户 " + foundUser.getUsername());
        } else {
            System.out.println("Provider: 未找到用户，username=" + username);
        }
        return foundUser;
    }
} 