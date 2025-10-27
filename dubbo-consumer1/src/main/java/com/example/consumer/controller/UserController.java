package com.example.consumer.controller;

import com.example.api.UserService;
import com.example.api.UserService.User;
import com.example.api.UserService.CreateUserResult;
import com.example.api.UserService.UpdateUserResult;
import com.example.api.UserService.DeleteUserResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务 REST API 控制器
 * 
 * @author example
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.info("健康检查请求");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "Dubbo3 Consumer");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试接口
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        logger.info("测试接口请求开始");
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Dubbo3 Consumer is running!");
        result.put("timestamp", System.currentTimeMillis());
        
        try {
            logger.info("准备调用 userService.getAllUsers()");
            // 测试获取所有用户
            List<User> users = userService.getAllUsers();
            logger.info("userService.getAllUsers() 调用成功，返回 {} 个用户", users.size());
            result.put("userCount", users.size());
            result.put("users", users);
            result.put("success", true);
        } catch (Exception e) {
            logger.error("测试调用Provider失败", e);
            result.put("error", e.getMessage());
            result.put("success", false);
            result.put("exceptionType", e.getClass().getSimpleName());
        }
        
        logger.info("测试接口请求结束");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取所有用户
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        logger.info("获取所有用户请求");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<User> users = userService.getAllUsers();
            result.put("success", true);
            result.put("data", users);
            result.put("count", users.size());
            result.put("message", "获取用户列表成功");
            
            logger.info("获取用户列表成功，共 {} 个用户", users.size());
            
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
            result.put("success", false);
            result.put("message", "获取用户列表失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 根据ID获取用户
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        logger.info("获取用户请求，用户ID: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            User user = userService.getUserById(id);
            
            if (user != null) {
                result.put("success", true);
                result.put("data", user);
                result.put("message", "获取用户信息成功");
                logger.info("获取用户信息成功: {}", user);
            } else {
                result.put("success", false);
                result.put("message", "用户不存在，ID: " + id);
                logger.warn("用户不存在，ID: {}", id);
            }
            
        } catch (Exception e) {
            logger.error("获取用户信息失败", e);
            result.put("success", false);
            result.put("message", "获取用户信息失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 创建用户
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody User user) {
        logger.info("创建用户请求: {}", user);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            CreateUserResult createResult = userService.createUser(user);
            
            if (createResult.isSuccess()) {
                result.put("success", true);
                result.put("data", createResult);
                result.put("message", "用户创建成功");
                logger.info("用户创建成功，用户ID: {}", createResult.getUserId());
            } else {
                result.put("success", false);
                result.put("message", createResult.getMessage());
                logger.warn("用户创建失败: {}", createResult.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("创建用户失败", e);
            result.put("success", false);
            result.put("message", "创建用户失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 更新用户
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody User user) {
        logger.info("更新用户请求，用户ID: {}, 用户信息: {}", id, user);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            user.setId(id);
            UpdateUserResult updateResult = userService.updateUser(user);
            
            if (updateResult.isSuccess()) {
                result.put("success", true);
                result.put("message", "用户更新成功");
                logger.info("用户更新成功，用户ID: {}", id);
            } else {
                result.put("success", false);
                result.put("message", updateResult.getMessage());
                logger.warn("用户更新失败: {}", updateResult.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("更新用户失败", e);
            result.put("success", false);
            result.put("message", "更新用户失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        logger.info("删除用户请求，用户ID: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            DeleteUserResult deleteResult = userService.deleteUser(id);
            
            if (deleteResult.isSuccess()) {
                result.put("success", true);
                result.put("message", "用户删除成功");
                logger.info("用户删除成功，用户ID: {}", id);
            } else {
                result.put("success", false);
                result.put("message", deleteResult.getMessage());
                logger.warn("用户删除失败: {}", deleteResult.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("删除用户失败", e);
            result.put("success", false);
            result.put("message", "删除用户失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 搜索用户
     */
    @GetMapping("/users/search")
    public ResponseEntity<Map<String, Object>> searchUsers(@RequestParam String username) {
        logger.info("搜索用户请求，用户名: {}", username);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<User> users = userService.searchUsersByUsername(username);
            result.put("success", true);
            result.put("data", users);
            result.put("count", users.size());
            result.put("message", "搜索用户成功");
            
            logger.info("搜索用户成功，找到 {} 个用户", users.size());
            
        } catch (Exception e) {
            logger.error("搜索用户失败", e);
            result.put("success", false);
            result.put("message", "搜索用户失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 全局异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("控制器异常", e);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "服务器内部错误: " + e.getMessage());
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(500).body(result);
    }
} 