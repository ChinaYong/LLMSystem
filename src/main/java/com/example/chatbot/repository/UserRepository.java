package com.example.chatbot.repository;

import com.example.chatbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户数据访问接口
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // 根据用户名查询用户
    User findByUsername(String username);
}
