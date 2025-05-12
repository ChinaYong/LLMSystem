package com.example.chatbot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库中的 users 表
 */
@Setter
@Getter
@Entity              // 标记这是一个 JPA 实体
@Table(name = "users") // 指定数据库表名为 users
public class User {

    // 主键 id，自增
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 登录用户名，唯一
    @Column(unique = true, nullable = false)
    private String username;

    // 登录密码，存储加密后的哈希值
    @Column(nullable = false)
    private String password;
    
    // 用户邮箱
    @Column(unique = true)
    private String email;
    
    // 用户真实姓名
    private String fullName;
    
    // 用户角色：ROLE_USER, ROLE_ADMIN 等
    private String role = "ROLE_USER";
    
    // 账号是否启用
    private boolean enabled = true;
    
    // 账号是否未过期
    private boolean accountNonExpired = true;
    
    // 账号是否未锁定
    private boolean accountNonLocked = true;
    
    // 凭证是否未过期
    private boolean credentialsNonExpired = true;
    
    // 用户创建时间
    private LocalDateTime createdAt;
    
    // 最后登录时间
    private LocalDateTime lastLoginAt;

    // ----------- 以下为 getter 和 setter 方法 -----------

    // JPA 需要有无参构造函数
    public User() {
        this.createdAt = LocalDateTime.now();
    }
    
    // 创建用户的构造函数
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    // 可选：重写 toString，方便调试时打印
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
