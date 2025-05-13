package com.example.chatbot.controller;

import com.example.chatbot.model.User;
import com.example.chatbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * 认证控制器，处理用户注册、登录等认证相关请求
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = Logger.getLogger(AuthController.class.getName());

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    /**
     * 用户注册
     * @param user 用户信息
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        logger.info("收到注册请求: " + user.getUsername());
        try {
            User registeredUser = userService.createUser(user);
            logger.info("用户注册成功: " + registeredUser.getUsername());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "注册成功");
            response.put("userId", registeredUser.getId());
            response.put("username", registeredUser.getUsername());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warning("用户注册失败(参数错误): " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.severe("用户注册失败(系统错误): " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "注册失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 用户登录
     * @param loginRequest 登录请求参数
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        String role = loginRequest.get("role"); // 获取前端传入的角色参数
        
        logger.info("收到登录请求: 用户名=" + username + ", 角色=" + role);

        try {
            // 先检查用户是否存在
            User existingUser = userService.findByUsername(username);
            if (existingUser == null) {
                logger.warning("用户不存在: " + username);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            // 输出用户状态信息
        logger.info("用户 " + username + " 登录成功，状态信息: " +
                "ID: " + existingUser.getId() +
                ", 用户名: " + existingUser.getUsername() +
                ", 角色: " + existingUser.getRole() +
                ", 会话ID: " + existingUser.getSessionId() +
                ", 创建时间: " + existingUser.getCreatedAt());

            // 使用Spring Security进行认证
            logger.info("开始认证用户: " + username);
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            
            // 设置认证信息到上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.info("用户认证成功: " + username);
            
            // 获取用户信息
            Optional<User> userOpt = userService.login(username, password);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.info("获取用户信息成功: " + user.getUsername() + ", 角色=" + user.getRole());
                
                // 如果前端提供了角色，且用户有权限修改角色，则更新用户角色
                if (role != null && !role.isEmpty() && user.getRole().equals("ROLE_ADMIN")) {
                    user.setRole(role);
                    userService.updateUserRole(user.getId(), role);
                    logger.info("更新用户角色: " + username + ", 新角色=" + role);
                }
                
                // 更新最后登录时间
                userService.updateLastLoginTime(user.getId());
                
                // 构建响应
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "登录成功");
                response.put("userId", user.getId());
                response.put("username", user.getUsername());
                response.put("role", user.getRole());
                
                logger.info("用户登录成功: " + username);
                return ResponseEntity.ok(response);
            } else {
                logger.warning("用户信息获取失败: " + username);
                throw new AuthenticationException("认证失败") {};
            }
        } catch (AuthenticationException e) {
            logger.warning("用户认证失败: " + username + ", 原因: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "用户名或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * 获取当前登录用户信息
     * @return 用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
            logger.warning("获取当前用户信息失败: 未登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }
        
        String username = authentication.getName();
        logger.info("获取当前用户信息: " + username);
        User user = userService.findByUsername(username);
        
        if (user != null) {
            Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("sessionId", user.getSessionId());
        response.put("role", user.getRole());
            logger.info("当前用户信息获取成功: " + username);
            return ResponseEntity.ok(response);
        } else {
            logger.warning("当前用户信息获取失败: 用户不存在 " + username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("用户不存在");
        }
    }

    /**
     * 修改密码
     * @param passwordRequest 密码修改请求
     * @return 修改结果
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> passwordRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
            logger.warning("密码修改失败: 未登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }
        
        String username = authentication.getName();
        logger.info("密码修改请求: " + username);
        User user = userService.findByUsername(username);
        
        if (user != null) {
            String oldPassword = passwordRequest.get("oldPassword");
            String newPassword = passwordRequest.get("newPassword");
            
            if (userService.changePassword(user.getId(), oldPassword, newPassword)) {
                logger.info("密码修改成功: " + username);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "密码修改成功");
                return ResponseEntity.ok(response);
            } else {
                logger.warning("密码修改失败: 原密码错误 " + username);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "原密码错误");
                return ResponseEntity.badRequest().body(response);
            }
        } else {
            logger.warning("密码修改失败: 用户不存在 " + username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("用户不存在");
        }
    }

    /**
     * 登出
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !"anonymousUser".equals(authentication.getPrincipal())) {
            logger.info("用户登出: " + authentication.getName());
        }
        SecurityContextHolder.clearContext();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "已成功登出");
        return ResponseEntity.ok(response);
    }

    /**
     * 测试端点，用于验证用户凭据和密码编码器
     * @return 验证结果
     */
    @GetMapping("/test-auth")
    public ResponseEntity<?> testAuth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 测试admin用户
            User adminUser = userService.findByUsername("admin");
            if (adminUser != null) {
                response.put("admin_exists", true);
                response.put("admin_password_hash", adminUser.getPassword());
//                response.put("admin_enabled", adminUser.isEnabled());
//                response.put("admin_account_non_expired", adminUser.isAccountNonExpired());
//                response.put("admin_account_non_locked", adminUser.isAccountNonLocked());
//                response.put("admin_credentials_non_expired", adminUser.isCredentialsNonExpired());
                
                // 测试明文密码与哈希值匹配
                boolean adminPasswordMatches = userService.login("admin", "admin123").isPresent();
                response.put("admin_password_matches", adminPasswordMatches);
            } else {
                response.put("admin_exists", false);
            }
            
            // 测试user用户
            User normalUser = userService.findByUsername("user");
            if (normalUser != null) {
                response.put("user_exists", true);
                response.put("user_password_hash", normalUser.getPassword());
//                response.put("user_enabled", normalUser.isEnabled());
//                response.put("user_account_non_expired", normalUser.isAccountNonExpired());
//                response.put("user_account_non_locked", normalUser.isAccountNonLocked());
//                response.put("user_credentials_non_expired", normalUser.isCredentialsNonExpired());
                
                // 测试明文密码与哈希值匹配
                boolean userPasswordMatches = userService.login("user", "user123").isPresent();
                response.put("user_password_matches", userPasswordMatches);
            } else {
                response.put("user_exists", false);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}