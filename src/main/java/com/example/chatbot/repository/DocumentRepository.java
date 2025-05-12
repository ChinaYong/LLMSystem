package com.example.chatbot.repository;

import com.example.chatbot.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 文档元数据访问接口
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
}
