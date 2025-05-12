package com.example.chatbot.controller;

import com.example.chatbot.model.Document;
import com.example.chatbot.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文件上传接口
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 上传知识库文件
     * @param file MultipartFile（支持 txt、pdf、docx…）
     */
    @PostMapping("/upload")
    public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file) {
        Document doc = knowledgeService.uploadAndProcess(file);
        return ResponseEntity.ok(doc);
    }
    
    /**
     * 获取所有上传的知识库文件
     * @return 文件列表
     */
    @GetMapping("/files")
    public ResponseEntity<List<Document>> getAllFiles() {
        List<Document> files = knowledgeService.getAllDocuments();
        return ResponseEntity.ok(files);
    }
}