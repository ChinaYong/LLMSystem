package com.example.chatbot.controller;

import com.example.chatbot.model.Document;
import com.example.chatbot.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 知识库文件上传接口
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger logger = Logger.getLogger(KnowledgeController.class.getName());

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 上传知识库文件
     * @param file MultipartFile（支持 txt、pdf、docx…）
     * @param userId 上传用户的ID（可选，如未提供则使用当前登录用户ID）
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "userId", required = false) Long userId) {
        
        try {
            logger.info("开始处理文件上传: " + file.getOriginalFilename() + 
                       ", 大小: " + file.getSize() + 
                       ", 用户ID: " + (userId != null ? userId : "当前登录用户"));
            
            // 如果提供了userId参数，则使用它，否则让服务层处理
            Document doc = userId != null ? 
                    knowledgeService.uploadAndProcess(file, userId) : 
                    knowledgeService.uploadAndProcess(file);
            
            // 构建标准的响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文件上传成功");
            response.put("document", doc);
            
            logger.info("文件上传成功: " + file.getOriginalFilename());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.severe("文件上传失败: " + e.getMessage());
            e.printStackTrace();
            
            // 构建错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "文件上传失败: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorResponse);
        }
    }
    
    /**
     * 获取所有上传的知识库文件
     * 基于当前用户的权限：
     * - 管理员可查看所有文件
     * - 普通用户只能查看自己上传的文件
     * @return 文件列表
     */
    @GetMapping("/files")
    public ResponseEntity<List<Document>> getAllFiles() {
        List<Document> files = knowledgeService.getAllDocuments();
        return ResponseEntity.ok(files);
    }
    
    /**
     * 获取指定用户上传的知识库文件
     * 注意：只有管理员可调用此接口查看其他用户的文件
     * @param userId 用户ID
     * @return 该用户上传的文件列表
     */
    @GetMapping("/files/user/{userId}")
    public ResponseEntity<List<Document>> getUserFiles(@PathVariable Long userId) {
        // 内部会检查权限，只有管理员或本人可以查看
        List<Document> files = knowledgeService.getDocuments(userId, false);
        return ResponseEntity.ok(files);
    }
}