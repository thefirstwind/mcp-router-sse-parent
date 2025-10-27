package com.example.provider.service;

import com.example.api.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 
 * @author example
 * @version 1.0.0
 */
@Service
@DubboService(version = "1.0.0", timeout = 10000, retries = 2)
public class OrderServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    // 模拟数据库存储
    private final Map<Long, User> userMap = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public OrderServiceImpl() {
        // 初始化一些测试数据
        initTestData();
    }
    
    @Override
    public User getUserById(Long userId) {
        logger.info("获取用户信息，用户ID: {}", userId);
        
        User user = userMap.get(userId);
        if (user == null) {
            logger.warn("用户不存在，用户ID: {}", userId);
            return null;
        }
        
        logger.info("获取用户信息成功: {}", user);
        return user;
    }
    
    @Override
    public CreateUserResult createUser(User user) {
        logger.info("创建用户: {}", user);
        
        try {
            // 参数校验
            if (user == null) {
                return CreateUserResult.failure("用户信息不能为空");
            }
            
            if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                return CreateUserResult.failure("用户名不能为空");
            }
            
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                return CreateUserResult.failure("邮箱不能为空");
            }
            
            // 检查用户名是否已存在
            boolean usernameExists = userMap.values().stream()
                    .anyMatch(u -> u.getUsername().equals(user.getUsername()));
            if (usernameExists) {
                return CreateUserResult.failure("用户名已存在: " + user.getUsername());
            }
            
            // 生成用户ID
            Long userId = idGenerator.getAndIncrement();
            user.setId(userId);
            user.setCreateTime(System.currentTimeMillis());
            user.setUpdateTime(System.currentTimeMillis());
            
            // 保存用户
            userMap.put(userId, user);
            
            logger.info("用户创建成功，用户ID: {}", userId);
            return CreateUserResult.success(userId);
            
        } catch (Exception e) {
            logger.error("创建用户失败", e);
            return CreateUserResult.failure("创建用户失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<User> getAllUsers() {
        logger.info("获取所有用户列表");
        
        List<User> users = new ArrayList<>(userMap.values());
        logger.info("获取用户列表成功，共 {} 个用户", users.size());
        
        return users;
    }
    
    @Override
    public List<User> searchUsersByUsername(String username) {
        logger.info("根据用户名搜索用户: {}", username);
        
        if (username == null || username.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<User> users = userMap.values().stream()
                .filter(user -> user.getUsername().toLowerCase().contains(username.toLowerCase()))
                .collect(Collectors.toList());
        
        logger.info("搜索用户成功，找到 {} 个用户", users.size());
        return users;
    }
    
    @Override
    public UpdateUserResult updateUser(User user) {
        logger.info("更新用户信息: {}", user);
        
        try {
            // 参数校验
            if (user == null || user.getId() == null) {
                return UpdateUserResult.failure("用户ID不能为空");
            }
            
            User existingUser = userMap.get(user.getId());
            if (existingUser == null) {
                return UpdateUserResult.failure("用户不存在，用户ID: " + user.getId());
            }
            
            // 检查用户名是否被其他用户使用
            if (user.getUsername() != null && !user.getUsername().equals(existingUser.getUsername())) {
                boolean usernameExists = userMap.values().stream()
                        .anyMatch(u -> !u.getId().equals(user.getId()) && u.getUsername().equals(user.getUsername()));
                if (usernameExists) {
                    return UpdateUserResult.failure("用户名已存在: " + user.getUsername());
                }
            }
            
            // 更新用户信息
            if (user.getUsername() != null) {
                existingUser.setUsername(user.getUsername());
            }
            if (user.getEmail() != null) {
                existingUser.setEmail(user.getEmail());
            }
            if (user.getPhone() != null) {
                existingUser.setPhone(user.getPhone());
            }
            if (user.getAge() != null) {
                existingUser.setAge(user.getAge());
            }
            if (user.getAddress() != null) {
                existingUser.setAddress(user.getAddress());
            }
            
            existingUser.setUpdateTime(System.currentTimeMillis());
            
            logger.info("用户信息更新成功，用户ID: {}", user.getId());
            return UpdateUserResult.success();
            
        } catch (Exception e) {
            logger.error("更新用户信息失败", e);
            return UpdateUserResult.failure("更新用户信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public DeleteUserResult deleteUser(Long userId) {
        logger.info("删除用户，用户ID: {}", userId);
        
        try {
            if (userId == null) {
                return DeleteUserResult.failure("用户ID不能为空");
            }
            
            User user = userMap.remove(userId);
            if (user == null) {
                return DeleteUserResult.failure("用户不存在，用户ID: " + userId);
            }
            
            logger.info("用户删除成功，用户ID: {}", userId);
            return DeleteUserResult.success();
            
        } catch (Exception e) {
            logger.error("删除用户失败", e);
            return DeleteUserResult.failure("删除用户失败: " + e.getMessage());
        }
    }
    
    @Override
    public String healthCheck() {
        logger.info("健康检查");
        return "OK - Dubbo2 Provider is running, user count: " + userMap.size();
    }
    
    /**
     * 初始化测试数据
     */
    private void initTestData() {
        logger.info("初始化测试数据...");
        
        // 创建测试用户
        User user1 = new User();
        user1.setId(idGenerator.getAndIncrement());
        user1.setUsername("admin");
        user1.setEmail("admin@example.com");
        user1.setPhone("13800138001");
        user1.setAge(30);
        user1.setAddress("北京市朝阳区");
        user1.setCreateTime(System.currentTimeMillis());
        user1.setUpdateTime(System.currentTimeMillis());
        
        User user2 = new User();
        user2.setId(idGenerator.getAndIncrement());
        user2.setUsername("test");
        user2.setEmail("test@example.com");
        user2.setPhone("13800138002");
        user2.setAge(25);
        user2.setAddress("上海市浦东新区");
        user2.setCreateTime(System.currentTimeMillis());
        user2.setUpdateTime(System.currentTimeMillis());
        
        User user3 = new User();
        user3.setId(idGenerator.getAndIncrement());
        user3.setUsername("demo");
        user3.setEmail("demo@example.com");
        user3.setPhone("13800138003");
        user3.setAge(28);
        user3.setAddress("深圳市南山区");
        user3.setCreateTime(System.currentTimeMillis());
        user3.setUpdateTime(System.currentTimeMillis());
        
        // 保存到内存中
        userMap.put(user1.getId(), user1);
        userMap.put(user2.getId(), user2);
        userMap.put(user3.getId(), user3);
        
        logger.info("测试数据初始化完成，共 {} 个用户", userMap.size());
    }
} 