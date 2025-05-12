package com.example.chatbot.repository;

import com.example.chatbot.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 聊天记录数据访问接口
 */
@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    // 如有需要，可自定义更多查询方法
}
