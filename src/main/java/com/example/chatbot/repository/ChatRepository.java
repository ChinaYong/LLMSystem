package com.example.chatbot.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.chatbot.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天记录数据访问接口
 */
@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    /**
     * 根据用户ID查询聊天记录
     * @param userId 用户ID
     * @return 该用户的所有聊天记录
     */
    List<Chat> findByUserId(Long userId);
    
    /**
     * 根据会话ID查询聊天记录
     * @param sessionId 会话ID
     * @return 该会话的所有聊天记录
     */
    List<Chat> findBySessionId(String sessionId);
    
    /**
     * 根据用户ID查询聊天记录并按sessionId排序
     * @param userId 用户ID
     * @return 该用户的所有聊天记录，按sessionId排序
     */
    @Query("SELECT c FROM Chat c WHERE c.userId = :userId ORDER BY c.sessionId, c.createdAt")
    List<Chat> findByUserIdOrderBySessionId(@Param("userId") Long userId);
    
    /**
     * 查询用户的所有会话ID（去重）
     * @param userId 用户ID
     * @return 该用户的所有会话ID列表
     */
    @Query("SELECT DISTINCT c.sessionId FROM Chat c WHERE c.userId = :userId")
    List<String> findDistinctSessionIdsByUserId(@Param("userId") Long userId);
}
