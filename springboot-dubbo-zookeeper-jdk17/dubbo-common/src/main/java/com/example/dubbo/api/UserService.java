package com.example.dubbo.api;

import com.example.dubbo.model.User;
import java.util.List;

/**
 * 用户服务接口
 * Dubbo服务接口定义
 */
public interface UserService {
    
    /**
     * 根据ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    User getUserById(Long userId);
    
    /**
     * 获取所有用户列表
     * @return 用户列表
     */
    List<User> getAllUsers();
    
    /**
     * 创建新用户
     * @param user 用户信息
     * @return 创建的用户ID
     */
    Long createUser(User user);
    
    /**
     * 更新用户信息
     * @param user 用户信息
     * @return 是否更新成功
     */
    boolean updateUser(User user);
    
    /**
     * 删除用户
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteUser(Long userId);
    
    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    User getUserByUsername(String username);
} 