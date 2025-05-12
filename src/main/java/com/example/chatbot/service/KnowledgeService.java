package com.example.chatbot.service;

import com.example.chatbot.model.Document;
import com.example.chatbot.model.Segment;
import com.example.chatbot.repository.DocumentRepository;
import com.example.chatbot.repository.SegmentRepository;
import com.example.chatbot.util.DocumentParser;
import com.example.chatbot.util.TextUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库管理服务：文件上传、存储、元数据入库
 */
@Service
public class KnowledgeService {

    private final DocumentRepository documentRepository;
    // 从配置注入目录
    private final Path uploadDir;
    private final SegmentRepository segmentRepo;
    private final EmbeddingService embeddingService;

    public KnowledgeService(DocumentRepository docRepo,
                            SegmentRepository segmentRepo,
                            EmbeddingService embeddingService,
                            @Value("${knowledge.upload-dir}") String uploadDir) throws IOException {
        this.documentRepository = docRepo;
        this.segmentRepo = segmentRepo;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.embeddingService = embeddingService;
        // 如果目录不存在就创建
        Files.createDirectories(this.uploadDir);
    }

    /**
     * 上传并保存文件
     * @param file Spring MVC 接收的 MultipartFile
     * @return 保存后的 Document 元数据
     */
    public Document importFile(MultipartFile file) throws IOException {
        // 1. 原始文件名
        String originalFilename = file.getOriginalFilename();
        // 2. 目标文件路径：uploadDir/{timestamp}_{原名}
        String storedName = System.currentTimeMillis() + "_" + originalFilename;
        Path target = uploadDir.resolve(storedName);
        // 3. 保存到本地
        file.transferTo(target.toFile());

        // 4. 入库
        Document doc = new Document();
        doc.setFilename(originalFilename);
        doc.setFilepath(target.toString());
        doc.setUploadTime(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    /**
     * 解析并分段入库
     */
    public void processDocument(Long docId) throws Exception {
        // 1. 查出文档元数据
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("找不到文档：" + docId));

        // 调试输出：查看路径
        System.out.println(">> 正在解析文档路径: " + doc.getFilepath());

        // 2. 解析为纯文本
        File file = new File(doc.getFilepath());
        System.out.println(">> 文件存在吗？ " + file.exists() + ", 大小 = " + file.length());
        String text = DocumentParser.parseToText(file);
        System.out.println(">> 解析后文本长度: " + text.length());
        System.out.println(">> 文本前 100 字: " + text.substring(0, Math.min(100, text.length())));

        // 3. 按 500 字拆分
        List<String> chunks = TextUtils.chunkText(text, 500);

        // 4. 保存每个片段
        for (String chunk : chunks) {
            Segment seg = new Segment();
            seg.setDocumentId(docId);
            seg.setContent(chunk);
            segmentRepo.save(seg);
            // 生成并存向量
            embeddingService.indexSegment(seg.getId(), chunk);
        }
    }
    
    /**
     * 上传文件并处理（一键上传、分段、建立索引）
     * @param file 要上传的文件
     * @return 文档元数据
     */
    public Document uploadAndProcess(MultipartFile file) {
        try {
            // 1. 上传并入库
            Document doc = importFile(file);
            
            // 2. 处理文档（分段、索引）
            processDocument(doc.getId());
            
            // 3. 返回处理后的文档元数据
            return doc;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("文件上传或处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文档 ID 获取所有分段
     */
    public List<Segment> getAllSegmentsByDocumentId(Long documentId) {
        return segmentRepo.findByDocumentId(documentId);
    }

    /**
     * 获取所有知识库文档
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }
}