package com.example.api;

import java.io.Serializable;
import java.util.List;

/**
 * 用户服务接口
 * 用于演示 Dubbo3 Consumer 调用 Dubbo2 Provider
 */
public interface UserService {
    
    /**
     * 根据用户ID获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    User getUserById(Long userId);
    
    /**
     * 创建用户
     * 
     * @param user 用户信息
     * @return 创建结果
     */
    CreateUserResult createUser(User user);
    
    /**
     * 获取所有用户列表
     * 
     * @return 用户列表
     */
    List<User> getAllUsers();
    
    /**
     * 根据用户名搜索用户
     * 
     * @param username 用户名
     * @return 用户列表
     */
    List<User> searchUsersByUsername(String username);
    
    /**
     * 更新用户信息
     * 
     * @param user 用户信息
     * @return 更新结果
     */
    UpdateUserResult updateUser(User user);
    
    /**
     * 删除用户
     * 
     * @param userId 用户ID
     * @return 删除结果
     */
    DeleteUserResult deleteUser(Long userId);
    
    /**
     * 健康检查
     * 
     * @return 服务状态
     */
    String healthCheck();
    
    /**
     * 用户信息实体类
     */
    class User implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long id;
        private String username;
        private String email;
        private String phone;
        private Integer age;
        private String address;
        private Long createTime;
        private Long updateTime;
        
        public User() {}
        
        public User(Long id, String username, String email) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.createTime = System.currentTimeMillis();
            this.updateTime = System.currentTimeMillis();
        }
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
        
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
        
        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", email='" + email + '\'' +
                    ", phone='" + phone + '\'' +
                    ", age=" + age +
                    ", address='" + address + '\'' +
                    ", createTime=" + createTime +
                    ", updateTime=" + updateTime +
                    '}';
        }
    }
    
    /**
     * 创建用户结果
     */
    class CreateUserResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private boolean success;
        private String message;
        private Long userId;
        
        public CreateUserResult() {}
        
        public CreateUserResult(boolean success, String message, Long userId) {
            this.success = success;
            this.message = message;
            this.userId = userId;
        }
        
        public static CreateUserResult success(Long userId) {
            return new CreateUserResult(true, "用户创建成功", userId);
        }
        
        public static CreateUserResult failure(String message) {
            return new CreateUserResult(false, message, null);
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
    
    /**
     * 更新用户结果
     */
    class UpdateUserResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private boolean success;
        private String message;
        
        public UpdateUserResult() {}
        
        public UpdateUserResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static UpdateUserResult success() {
            return new UpdateUserResult(true, "用户更新成功");
        }
        
        public static UpdateUserResult failure(String message) {
            return new UpdateUserResult(false, message);
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
    
    /**
     * 删除用户结果
     */
    class DeleteUserResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private boolean success;
        private String message;
        
        public DeleteUserResult() {}
        
        public DeleteUserResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static DeleteUserResult success() {
            return new DeleteUserResult(true, "用户删除成功");
        }
        
        public static DeleteUserResult failure(String message) {
            return new DeleteUserResult(false, message);
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
} 