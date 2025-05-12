package com.example.chatbot.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 文本片段（Chunk）实体：
 * 代表从知识库文档中切分出的一个小段落，用于检索和嵌入。
 */
@Data
@Entity
@Table(name = "segments")
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 所属 Document 的主键
    private Long documentId;

    // 这个片段的纯文本内容
    @Column(columnDefinition = "TEXT")
    private String content;

    // 预留：后面存 embedding 向量时用（暂时不入库）
    // @Lob
    // private byte[] embedding;

    // 新增：向量存储字段
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] vector;

}
