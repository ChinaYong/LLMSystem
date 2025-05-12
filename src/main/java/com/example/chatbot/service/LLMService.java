package com.example.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * 语言模型服务：调用 Ollama REST API
 */
@Service
public class LLMService {
    private static final Logger logger = Logger.getLogger(LLMService.class.getName());
    // Ollama服务状态标志
    private boolean ollamaServiceAvailable = true;
    private long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 30000; // 30秒检查一次服务状态

    private final WebClient client;

    @Value("${ollama.model:gemma3:4b}")
    private String model;

    @Value("${chat.mode:local}")
    private String chatMode;
    
    @Value("${chatbot.prompt.system:你是一个有用的AI助手}")
    private String systemPrompt;
    
    @Value("${chatbot.prompt.preventHallucination:}")
    private String preventHallucinationPrompt;
    
    @Value("${chatbot.prompt.citation:}")
    private String citationPrompt;
    
    @Value("${chatbot.prompt.formatInstruction:}")
    private String formatInstruction;

    public LLMService(WebClient ollamaClient) {
        this.client = ollamaClient;
    }

    /**
     * 根据用户问题和相关知识片段生成回答
     * @param question 用户问题
     * @param relevantSegments 相关知识片段
     * @return 生成的回答
     */
    public String generateAnswer(String question, List<String> relevantSegments) {
        // 如果Ollama服务不可用且在检查间隔内，直接返回离线回复
        if (!shouldTryConnectingToOllama()) {
            return getFallbackResponse(question);
        }

        try {
            // 将相关段落合并成一个上下文
            String context = relevantSegments.isEmpty() 
                ? "没有找到相关的知识库内容。" 
                : relevantSegments.stream()
                    .map(seg -> seg.trim())
                    .collect(Collectors.joining("\n\n"));
                    
            // 构建RAG提示词
            String fullPrompt = String.format(
                "%s\n\n" +
                "%s\n\n" + 
                "%s\n\n" +
                "%s\n\n" +
                "知识库内容：\n%s\n\n" +
                "用户问题：%s\n\n助手回答：",
                systemPrompt,
                preventHallucinationPrompt,
                citationPrompt,
                formatInstruction,
                context,
                question
            );

            logger.info("调用 LLM 生成回复，包含对话上下文，提示词长度: " + fullPrompt.length());

            // 构造请求体
            Map<String, Object> req = Map.of(
                    "model", model,
                    "prompt", fullPrompt,
                    "stream", false
            );

            // 发送请求
            Map<String, Object> resp = client.post()
                    .uri("/api/generate")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            // 服务连接成功，更新状态
            ollamaServiceAvailable = true;

            if (resp == null) {
                return "LLM 服务未返回有效响应";
            }

            // 提取回复文本
            String response = (String) resp.get("response");
            if (response != null) {
                System.out.println(">> 成功获取回复，长度: " + response.length());
                return response;
            }

            return "无法解析 LLM 返回的响应格式";
        } catch (WebClientRequestException e) {
            // 标记服务不可用
            ollamaServiceAvailable = false;
            lastCheckTime = System.currentTimeMillis();
            logger.severe("无法连接到Ollama服务: " + e.getMessage());
            return getFallbackResponse(question);
        } catch (Exception e) {
            logger.severe("调用 LLM 服务失败: " + e.getMessage());
            e.printStackTrace();
            return "服务调用出错: " + e.getMessage();
        }
    }

    /**
     * 根据用户问题、相关知识片段和对话历史生成回答
     * @param question 用户问题
     * @param relevantSegments 相关知识片段
     * @param conversationContext 对话历史上下文
     * @return 生成的回答
     */
    public String generateAnswerWithContext(String question, List<String> relevantSegments, String conversationContext) {
        // 如果Ollama服务不可用且在检查间隔内，直接返回离线回复
        if (!shouldTryConnectingToOllama()) {
            return getFallbackResponse(question);
        }

        try {
            // 将相关段落合并成一个上下文
            String context = relevantSegments.isEmpty()
                    ? "没有找到相关的知识库内容。"
                    : relevantSegments.stream()
                    .map(seg -> seg.trim())
                    .collect(Collectors.joining("\n\n"));

            // 构建RAG提示词，加入对话历史
            String fullPrompt = String.format(
                    "%s\n\n" +
                            "%s\n\n" +
                            "%s\n\n" +
                            "%s\n\n" +
                            "%s" + // 对话历史上下文（如果有）
                            "知识库内容：\n%s\n\n" +
                            "用户当前问题：%s\n\n助手回答：",
                    systemPrompt,
                    preventHallucinationPrompt,
                    citationPrompt,
                    formatInstruction,
                    conversationContext, // 可能为空
                    context,
                    question
            );

            logger.info("调用 LLM 生成回复，包含对话上下文，提示词以及历史对话，总长度: " + fullPrompt.length());

            // 构造请求体
            Map<String, Object> req = Map.of(
                    "model", model,
                    "prompt", fullPrompt,
                    "stream", false
            );

            // 发送请求
            Map<String, Object> resp = client.post()
                    .uri("/api/generate")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            // 服务连接成功，更新状态
            ollamaServiceAvailable = true;

            if (resp == null) {
                return "LLM 服务未返回有效响应";
            }

            // 提取回复文本
            String response = (String) resp.get("response");
            if (response != null) {
                logger.info("成功获取带上下文的回复，长度: " + response.length());
                logger.info("内容：\n" + response);
                return response;
            }

            return "无法解析 LLM 返回的响应格式";
        }catch (WebClientRequestException e) {
            // 标记服务不可用
            ollamaServiceAvailable = false;
            lastCheckTime = System.currentTimeMillis();
            logger.severe("无法连接到Ollama服务: " + e.getMessage());
            return getFallbackResponse(question);
        } catch (Exception e) {
            logger.severe("调用带上下文的 LLM 服务失败: " + e.getMessage());
            e.printStackTrace();
            return "服务调用出错: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    public String generateResponse(String userQuestion) {
        // 如果Ollama服务不可用且在检查间隔内，直接返回离线回复
        if (!shouldTryConnectingToOllama()) {
            return getFallbackResponse(userQuestion);
        }

        try {
            // 构建更完善的提示词
            String fullPrompt = String.format(
                "你是一个有用的AI助手，请用中文回答以下问题，给出详细的解释：\n\n用户问题：%s\n\n助手回答：",
                userQuestion
            );

            logger.info("调用 LLM 生成简单回复，提示词长度: " + fullPrompt.length());

            // 构造请求体
            Map<String, Object> req = Map.of(
                    "model", model,
                    "prompt", fullPrompt,
                    "stream", false
            );

            // 发送请求
            Map<String, Object> resp = client.post()
                    .uri("/api/generate")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            // 服务连接成功，更新状态
            ollamaServiceAvailable = true;

            if (resp == null) {
                return "LLM 服务未返回有效响应";
            }

            // 提取回复文本
            String response = (String) resp.get("response");
            if (response != null) {
                logger.info("成功获取简单回复，长度: " + response.length());
                return response;
            }

            return "无法解析 LLM 返回的响应格式";
        } catch (WebClientRequestException e) {
            // 标记服务不可用
            ollamaServiceAvailable = false;
            lastCheckTime = System.currentTimeMillis();
            logger.severe("无法连接到Ollama服务: " + e.getMessage());
            return getFallbackResponse(userQuestion);
        } catch (Exception e) {
            logger.severe("调用 LLM 服务失败: " + e.getMessage());
            e.printStackTrace();
            return "服务调用出错: " + e.getMessage();
        }
    }

    /**
     * 离线回复，当Ollama服务不可用时使用
     */
    private String getFallbackResponse(String question) {
        logger.info("生成离线回复，因为Ollama服务不可用");

        // 根据问题内容提供基本回复
        if (question.contains("你好") || question.contains("嗨") || question.contains("hi") || question.contains("hello")) {
            return "您好！我是AI助手。目前我正在离线模式下运行，某些功能可能受限。";
        } else if (question.contains("谢谢") || question.contains("感谢") || question.contains("thanks")) {
            return "不客气！很高兴能帮到您。目前我正在离线模式下运行，如有更多需求，可能需要等待在线服务恢复。";
        } else if (question.contains("再见") || question.contains("拜拜") || question.contains("bye")) {
            return "再见！如有需要随时回来咨询。";
        } else if (question.contains("帮助") || question.contains("help") || question.contains("怎么用")) {
            return "我是一个AI助手，可以回答问题和提供信息。目前我正在离线模式下运行，功能受限。正常情况下我可以回答知识性问题、提供建议等。";
        } else {
            return "抱歉，目前AI服务暂时不可用，无法回答您的问题。请确认Ollama服务是否已启动（默认端口11434），或稍后再试。";
        }
    }

    /**
     * 判断是否应该尝试连接Ollama服务
     */
    private boolean shouldTryConnectingToOllama() {
        // 如果服务可用，始终返回true
        if (ollamaServiceAvailable) {
            return true;
        }

        // 如果服务不可用，但已经超过了检查间隔，尝试重新连接
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime > CHECK_INTERVAL) {
            logger.info("尝试重新连接Ollama服务...");
            return true;
        }

        // 服务不可用且在检查间隔内，不尝试连接
        return false;
    }

    private String remoteChat(String prompt) {
        // TODO: 实现 OpenAI/DeepSeek 调用
        return "【远程 API 占位回答】";
    }
}