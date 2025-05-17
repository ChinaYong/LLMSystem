package com.example.chatbot.service;

import com.example.chatbot.model.Document;
import com.example.chatbot.model.Segment;
import com.example.chatbot.model.User;
import com.example.chatbot.repository.DocumentRepository;
import com.example.chatbot.repository.SegmentRepository;
import com.example.chatbot.repository.UserRepository;
import com.example.chatbot.util.DocumentParser;
import com.example.chatbot.util.TextUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * 知识库管理服务：文件上传、存储、元数据入库
 */
@Service
public class KnowledgeService {

    private static final Logger logger = Logger.getLogger(KnowledgeService.class.getName());

    private final DocumentRepository documentRepository;
    // 从配置注入目录
    private final Path uploadDir;
    private final SegmentRepository segmentRepo;
    private final EmbeddingService embeddingService;
    private final UserRepository userRepository;

    public KnowledgeService(DocumentRepository docRepo,
                            SegmentRepository segmentRepo,
                            EmbeddingService embeddingService,
                            UserRepository userRepository,
                            @Value("${knowledge.upload-dir}") String uploadDir) throws IOException {
        this.documentRepository = docRepo;
        this.segmentRepo = segmentRepo;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.embeddingService = embeddingService;
        this.userRepository = userRepository;
        // 如果目录不存在就创建
        Files.createDirectories(this.uploadDir);
    }

    /**
     * 上传并保存文件
     * @param file Spring MVC 接收的 MultipartFile
     * @param userId 上传用户的ID
     * @return 保存后的 Document 元数据
     */
    public Document importFile(MultipartFile file, Long userId) throws IOException {
        // 1. 原始文件名
        String originalFilename = file.getOriginalFilename();
        // 2. 目标文件路径：uploadDir/{timestamp}_{原名}
        String storedName = System.currentTimeMillis() + "_" + originalFilename;
        Path target = uploadDir.resolve(storedName);    //拼接路径与文件名
        // 3. 保存到本地
        file.transferTo(target.toFile());

        // 4. 入库
        Document doc = new Document();
        doc.setFilename(originalFilename);
        doc.setFilepath(target.toString());
        doc.setUploadTime(LocalDateTime.now());
        doc.setUserId(userId); // 设置用户ID
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
     * @param userId 用户ID
     * @return 文档元数据
     */
    public Document uploadAndProcess(MultipartFile file, Long userId) {
        try {
            logger.info("开始处理文件上传 - 文件名: " + file.getOriginalFilename() + 
                       ", 大小: " + file.getSize() + 
                       ", 用户ID: " + userId);
            
            // 1. 上传并入库
            Document doc = importFile(file, userId);
            logger.info("文件元数据已保存到数据库，文档ID: " + doc.getId());
            
            try {
                // 2. 处理文档（分段、索引）
                processDocument(doc.getId());
                logger.info("文件处理完成 - 文档ID: " + doc.getId());
            } catch (Exception e) {
                // 即使文件处理失败，也返回文档元数据，因为文件已经上传了
                logger.warning("文件处理失败，但文件已上传 - 文档ID: " + doc.getId() + 
                             ", 错误: " + e.getMessage());
                // 将异常打印到控制台，但不抛出
                e.printStackTrace();
            }
            
            // 3. 返回处理后的文档元数据
            return doc;
        } catch (Exception e) {
            logger.severe("文件上传完全失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("文件上传或处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 兼容原有接口的上传方法
     * @param file 要上传的文件
     * @return 文档元数据
     */
    public Document uploadAndProcess(MultipartFile file) {
        // 获取当前登录用户ID
        Long userId = getCurrentUserId();
        if (userId == null) {
            logger.warning("无法获取当前用户ID，使用默认用户ID 1");
            userId = 1L; // 如果无法获取用户ID，使用默认值
        }
        return uploadAndProcess(file, userId);
    }

    /**
     * 根据文档 ID 获取所有分段
     */
    public List<Segment> getAllSegmentsByDocumentId(Long documentId) {
        return segmentRepo.findByDocumentId(documentId);
    }

    /**
     * 获取所有知识库文档
     * 如果是管理员，返回所有文档
     * 如果是普通用户，仅返回该用户上传的文档
     * @param userId 当前用户ID
     * @param isAdmin 是否为管理员
     * @return 文档列表
     */
    public List<Document> getDocuments(Long userId, boolean isAdmin) {
        if (isAdmin) {
            // 管理员可以查看所有文档
            return documentRepository.findAll();
        } else {
            // 普通用户只能查看自己上传的文档
            return documentRepository.findByUserId(userId);
        }
    }
    
    /**
     * 获取所有知识库文档 - 兼容原有接口
     */
    public List<Document> getAllDocuments() {
        // 获取当前用户
        Long userId = getCurrentUserId();
        boolean isAdmin = isCurrentUserAdmin();
        
        // 根据用户身份获取相应文档
        return getDocuments(userId, isAdmin);
    }
    
    /**
     * 获取当前登录用户的ID
     * @return 用户ID，未登录则返回null
     */
    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                    !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                User user = userRepository.findByUsername(username);
                if (user != null) {
                    return user.getId();
                }
            }
        } catch (Exception e) {
            logger.warning("获取当前用户ID失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 检查当前登录用户是否为管理员
     * @return 是否为管理员
     */
    private boolean isCurrentUserAdmin() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            }
        } catch (Exception e) {
            logger.warning("检查用户权限失败: " + e.getMessage());
        }
        return false;
    }
}