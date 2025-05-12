package com.example.chatbot.repository;

import com.example.chatbot.model.Segment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文本片段 数据访问接口
 */
@Repository
public interface SegmentRepository extends JpaRepository<Segment, Long> {
    // 日后可以按 documentId 查询：List<Segment> findByDocumentId(Long documentId);
    /**
     * 根据文档 ID 查询所有分段
     */
    List<Segment> findByDocumentId(Long documentId);
}
