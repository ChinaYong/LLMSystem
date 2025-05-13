package com.example.chatbot.repository;

import com.example.chatbot.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档元数据访问接口
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    /**
     * 根据用户ID查询该用户上传的所有文档
     * @param userId 用户ID
     * @return 该用户上传的文档列表
     */
    List<Document> findByUserId(Long userId);
}
