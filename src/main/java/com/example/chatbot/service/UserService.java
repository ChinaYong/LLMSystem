package com.example.chatbot.service;

import com.example.chatbot.model.User;
import com.example.chatbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户业务逻辑层，封装对 UserRepository 的调用
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private UserAuthService userAuthService;

    /** 
     * 创建新用户 - 使用UserAuthService进行注册
     * 此方法会对密码进行加密处理
     */
    public User createUser(User user) {
        return userAuthService.registerUser(user);
    }
    
    /**
     * 用户登录
     * @param username 用户名
     * @param password 明文密码
     * @return 登录成功的用户，如果认证失败返回空
     */
    public Optional<User> login(String username, String password) {
        return userAuthService.login(username, password);
    }

    /** 查询所有用户 */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /** 根据 ID 查询用户 */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /** 
     * 更新用户信息 
     * 注意：此方法不会更新密码，更新密码请使用changePassword方法
     */
    public Optional<User> updateUser(Long id, User updated) {
        return userRepository.findById(id)
                .map(existing -> {
                    // 更新基本信息
                    if (updated.getUsername() != null) {
                        existing.setUsername(updated.getUsername());
                    }
                    if (updated.getEmail() != null) {
                        existing.setEmail(updated.getEmail());
                    }
                    if (updated.getFullName() != null) {
                        existing.setFullName(updated.getFullName());
                    }
                    // 不更新密码，密码更新有专门的方法
                    return userRepository.save(existing);
                });
    }

    /** 删除用户 */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        return userAuthService.changePassword(userId, oldPassword, newPassword);
    }
    
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户对象，如果不存在返回null
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * 更新用户最后登录时间
     * @param userId 用户ID
     */
    public void updateLastLoginTime(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
    
    /**
     * 更新用户角色
     * @param userId 用户ID
     * @param role 新角色
     * @return 操作是否成功
     */
    public boolean updateUserRole(Long userId, String role) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setRole(role);
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }
}
