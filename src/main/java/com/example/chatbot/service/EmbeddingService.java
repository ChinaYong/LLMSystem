package com.example.chatbot.service;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.chatbot.repository.SegmentRepository;
import com.example.chatbot.model.Segment;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 嵌入向量服务：支持多种嵌入模式
 * 1. Ollama API（默认）
 * 2. 本地DJL实现
 * 3. 远程API（预留扩展）
 */
@Service
public class EmbeddingService {
    private static final Logger logger = Logger.getLogger(EmbeddingService.class.getName());
    
    private final WebClient client;
    // 内存索引：segmentId -> vector
    private final Map<Long, float[]> vectorIndex = new ConcurrentHashMap<>();

    @Value("${embedding.mode:local}")
    private String embeddingMode;

    @Value("${ollama.embedModel:nomic-embed-text}")
    private String embedModel;
    
    @Autowired
    private SegmentRepository segmentRepository;
    
    // DJL相关字段
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private boolean djlModelLoaded = false;

    public EmbeddingService(WebClient ollamaClient) {
        this.client = ollamaClient;
    }
    
    @PostConstruct
    public void initialize() {
        // 加载所有已有段落的向量到内存索引
        List<Segment> allSegments = segmentRepository.findAll();
        logger.info("正在加载 " + allSegments.size() + " 个片段的向量到内存...");
        
        int vectorsLoaded = 0;
        for (Segment segment : allSegments) {
            if (segment.getVector() != null) {
                float[] vector = deserializeVector(segment.getVector());
                vectorIndex.put(segment.getId(), vector);
                vectorsLoaded++;
            }
        }
        
        logger.info("成功加载 " + vectorsLoaded + " 个向量到内存索引");
        
        // 如果配置了DJL模式，尝试加载模型
        if ("djl".equalsIgnoreCase(embeddingMode)) {
            loadDjlModel();
        }
    }
    
    @PreDestroy
    public void cleanup() {
        // 释放DJL资源
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }
    
    /**
     * 加载DJL模型
     */
    private void loadDjlModel() {
        try {
            logger.info("正在加载DJL嵌入模型...");
            
            // 创建模型加载条件
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelName("nomic-embed-text")  // 可配置为其他模型
                    .optEngine("PyTorch")
                    .optDevice(Device.cpu())  // 或 Device.gpu()
                    .build();
            
            // 加载模型
            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();
            djlModelLoaded = true;
            
            logger.info("DJL嵌入模型加载成功");
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            logger.severe("DJL模型加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 核心对外方法：返回一段文本的 embedding 向量
     */
    public float[] embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warning("尝试对空文本进行嵌入，返回零向量");
            return new float[512]; // 返回零向量
        }
        
        try {
            switch (embeddingMode.toLowerCase()) {
                case "ollama":
                    return embedViaOllama(text);
                case "djl":
                    return embedViaDJL(text);
                case "remote":
                    return embedViaRemoteApi(text);
                default:
                    logger.warning("未知的嵌入模式: " + embeddingMode + "，使用Ollama作为后备");
                    return embedViaOllama(text);
            }
        } catch (Exception e) {
            logger.severe("嵌入向量生成失败: " + e.getMessage());
            e.printStackTrace();
            // 出错时返回空向量，但在生产环境可能需要更好的错误处理
            return new float[512];
        }
    }

    /**
     * 使用DJL本地生成嵌入向量
     */
    private float[] embedViaDJL(String text) {
        try {
            if (!djlModelLoaded) {
                loadDjlModel();
                if (!djlModelLoaded) {
                    logger.warning("DJL模型未加载，回退到Ollama API");
                    return embedViaOllama(text);
                }
            }
            
            logger.info("通过DJL生成嵌入向量，文本长度: " + text.length());
            float[] embedding = predictor.predict(text);
            logger.info("DJL嵌入向量生成成功，维度: " + embedding.length);
            return embedding;
        } catch (TranslateException e) {
            logger.severe("DJL嵌入生成失败: " + e.getMessage());
            logger.info("尝试回退到Ollama API");
            return embedViaOllama(text);
        }
    }

    /**
     * 用 Ollama REST API 获取 embedding
     */
    @SuppressWarnings("unchecked")
    private float[] embedViaOllama(String text) {
        try {
            logger.info("调用 Ollama Embedding API: model=" + embedModel + ", 文本长度=" + text.length());


            Map<String, Object> req = Map.of(
                    "model", embedModel,
                    "prompt", text
            );

            // 调用 /api/embeddings 端点
            Map<String, Object> resp = client.post()
                    .uri("/api/embeddings")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            logger.info("收到响应: " + (resp != null ? String.join(", ", resp.keySet()) : "null"));

            // 解析返回 JSON - Ollama API 直接返回 {"embedding": [...]} 而不是嵌套在 data 中
            if (resp == null || !resp.containsKey("embedding")) {
                throw new RuntimeException("Ollama Embeddings API 未返回 embedding 字段");
            }

            // 直接获取 embedding 数组
            List<Number> emb = (List<Number>) resp.get("embedding");
            float[] vector = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vector[i] = emb.get(i).floatValue();
            }

            logger.info("成功获取嵌入向量，维度: " + vector.length);
            return vector;
        } catch (Exception e) {
            logger.severe("Ollama嵌入向量调用失败: " + e.getMessage());
            e.printStackTrace();
            // 出错时返回空向量
            return new float[512];
        }
    }
    
    /**
     * 远程 Embedding API 实现
     */
    private float[] embedViaRemoteApi(String text) {
        // 这里可以实现第三方API如OpenAI等
        logger.warning("远程API嵌入尚未实现，返回零向量");
        return new float[512];
    }

    /**
     * 将指定文本片段索引到内存 map 中并持久化到数据库
     */
    public void indexSegment(Long segmentId, String content) {
        try {
            float[] vector = embedText(content);
            
            // 保存到内存索引
            vectorIndex.put(segmentId, vector);
            
            // 持久化到数据库
            Segment segment = segmentRepository.findById(segmentId)
                    .orElseThrow(() -> new RuntimeException("找不到ID为 " + segmentId + " 的片段"));
            
            segment.setVector(serializeVector(vector));
            segmentRepository.save(segment);
            
            logger.info("成功索引片段 " + segmentId + "，向量维度: " + vector.length);
        } catch (Exception e) {
            logger.severe("索引片段 " + segmentId + " 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取已索引的向量
     */
    public float[] getVector(Long segmentId) {
        return vectorIndex.get(segmentId);
    }

    /**
     * 余弦相似度计算
     */
    public float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0f;
        }
        
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10));
    }

//    /**
//     * 给定一个查询向量，返回内存中与之最相似的 Top-K 段 ID 列表
//     */
//    public List<Long> searchTopK(float[] queryVector, int k) {
//        if (queryVector == null || vectorIndex.isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        return vectorIndex.entrySet().stream()
//                .map(e -> Map.entry(e.getKey(), cosineSimilarity(queryVector, e.getValue())))
//                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
//                .limit(k)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//    }

    /**
     * 带有相似度阈值的检索方法
     */
    public List<Long> searchTopKWithThreshold(float[] queryVector, int k, float minSimilarity) {
        if (queryVector == null || vectorIndex.isEmpty()) {
            return Collections.emptyList();
        }
        
        return vectorIndex.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), cosineSimilarity(queryVector, e.getValue())))
                .filter(e -> e.getValue() >= minSimilarity) // 添加相似度阈值过滤
                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

//    /**
//     * 返回带得分的检索结果
//     */
//    public List<Map.Entry<Long, Float>> searchTopKWithScores(float[] queryVector, int k) {
//        if (queryVector == null || vectorIndex.isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        return vectorIndex.entrySet().stream()
//                .map(e -> Map.entry(e.getKey(), cosineSimilarity(queryVector, e.getValue())))
//                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
//                .limit(k)
//                .collect(Collectors.toList());
//    }

    /**
     * 根据用户问题查找相关的知识片段
     * @param question 用户问题
     * @param limit 返回的最大片段数量
     * @return 相关知识片段列表
     */
    public List<String> findRelevantSegments(String question, int limit) {
        try {
            // 基于相似度检索流程：
            // 1. 生成问题的向量表示
            float[] questionVector = embedText(question);
            
            // 2. 找出最相似的片段ID（相似度阈值为0.7）
            List<Long> topSegmentIds = searchTopKWithThreshold(questionVector, limit, 0.7f);
            if (topSegmentIds.isEmpty()) {
                logger.info("没有找到相关的知识片段");
                return Collections.emptyList();
            }
            
            // 3. 查询这些片段的内容
            List<Segment> segments = segmentRepository.findAllById(topSegmentIds);
            //打印出Segment内容
            for(Segment segment : segments) {
                logger.info("参考的片段：" + segment.getContent());
            }
            // 4. 提取内容并返回
            return segments.stream()
                    .map(Segment::getContent)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.severe("查询相关片段时出错: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    /**
     * 将向量序列化为数据库可存储的格式
     */
    private byte[] serializeVector(float[] vector) {
        byte[] bytes = new byte[vector.length * 4];
        for (int i = 0; i < vector.length; i++) {
            int intBits = Float.floatToIntBits(vector[i]);
            bytes[i * 4] = (byte) (intBits >> 24);
            bytes[i * 4 + 1] = (byte) (intBits >> 16);
            bytes[i * 4 + 2] = (byte) (intBits >> 8);
            bytes[i * 4 + 3] = (byte) (intBits);
        }
        return bytes;
    }
    
    /**
     * 从数据库存储格式反序列化向量
     */
    private float[] deserializeVector(byte[] bytes) {
        if (bytes == null) return null;
        
        int vectorSize = bytes.length / 4;
        float[] vector = new float[vectorSize];
        
        for (int i = 0; i < vectorSize; i++) {
            int intBits = ((bytes[i * 4] & 0xFF) << 24) | //与0xFF进行与计算会让byte先转换成int，计算后由于0xFF高位总是0，所以能去除高位符号，让byte转换成无符号int
                         ((bytes[i * 4 + 1] & 0xFF) << 16) | 
                         ((bytes[i * 4 + 2] & 0xFF) << 8) | 
                         (bytes[i * 4 + 3] & 0xFF);
            vector[i] = Float.intBitsToFloat(intBits);
        }
        return vector;
    }
    
    /**
     * 重新索引所有片段（可用于模型切换后刷新向量）
     */
    public void reindexAllSegments() {
        List<Segment> allSegments = segmentRepository.findAll();
        logger.info("开始重新索引 " + allSegments.size() + " 个片段...");
        
        int count = 0;
        for (Segment segment : allSegments) {
            indexSegment(segment.getId(), segment.getContent());
            count++;
            if (count % 100 == 0) {
                logger.info("已重新索引 " + count + " 个片段");
            }
        }
        
        logger.info("完成全部 " + count + " 个片段的重新索引");
    }
}