package com.example.chatbot.service;

import com.example.chatbot.controller.ConfigController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private final WebClient ollamaClient;
    private final WebClient deepseekClient;
    
    @Autowired
    private ConfigController configController;

    @Value("${ollama.model:gemma3:4b}")
    private String ollamaModel;
    
    @Value("${deepseek.model:deepseek-chat}")
    private String deepseekModel;
    
    @Value("${deepseek.temperature:0.7}")
    private double deepseekTemperature;
    
    @Value("${deepseek.max_tokens:2048}")
    private int deepseekMaxTokens;

    @Value("${chat.mode:local}")
    private String defaultChatMode;
    
    @Value("${chatbot.prompt.system:你是一个有用的AI助手}")
    private String systemPrompt;
    
    @Value("${chatbot.prompt.preventHallucination:}")
    private String preventHallucinationPrompt;
    
    @Value("${chatbot.prompt.citation:}")
    private String citationPrompt;
    
    @Value("${chatbot.prompt.formatInstruction:}")
    private String formatInstruction;

    public LLMService(
        @Qualifier("ollamaClient") WebClient ollamaClient,
        @Qualifier("deepseekClient") WebClient deepseekClient
    ) {
        this.ollamaClient = ollamaClient;
        this.deepseekClient = deepseekClient;
    }
    
    /**
     * 获取当前的聊天模式
     * @return 当前聊天模式，"local"或"remote"
     */
    private String getCurrentChatMode() {
        try {
            // 从ConfigController获取当前聊天模式
            Map<String, String> chatModeMap = configController.getChatMode();
            String currentMode = chatModeMap.get("mode");
            logger.info("从ConfigController获取的当前聊天模式: " + currentMode);
            return currentMode;
        } catch (Exception e) {
            logger.warning("获取聊天模式失败，使用默认模式: " + defaultChatMode + ", 错误: " + e.getMessage());
            return defaultChatMode;
        }
    }

    /**
     * 未使用，不包含历史对话逻辑
     * 根据用户问题和相关知识片段生成回答
     * @param question 用户问题
     * @param relevantSegments 相关知识片段
     * @return 生成的回答
     */
    public String generateAnswer(String question, List<String> relevantSegments) {
        // 检查是否使用远程模式
        String currentChatMode = getCurrentChatMode();
        if ("remote".equals(currentChatMode)) {
            System.out.println("[模式选择] 使用远程模式（DeepSeek API）");
            // 将相关段落合并成一个上下文
            String context = relevantSegments.isEmpty() 
                ? "没有找到相关的知识库内容。" 
                : relevantSegments.stream()
                    .map(seg -> seg.trim())
                    .collect(Collectors.joining("\n\n"));
                    
            // 构建消息列表
            List<Map<String, String>> messagesList = new ArrayList<>();
            
            // 添加系统提示消息
            if (!systemPrompt.isEmpty()) {
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                
                // 在系统提示中加入必要的指令
                String enhancedSystemPrompt = systemPrompt;
                if (!preventHallucinationPrompt.isEmpty()) {
                    enhancedSystemPrompt += "\n\n" + preventHallucinationPrompt;
                }
                if (!citationPrompt.isEmpty()) {
                    enhancedSystemPrompt += "\n\n" + citationPrompt;
                }
                if (!formatInstruction.isEmpty()) {
                    enhancedSystemPrompt += "\n\n" + formatInstruction;
                }
                
                systemMessage.put("content", enhancedSystemPrompt);
                messagesList.add(systemMessage);
            }
            
            // 添加用户消息，包含知识库上下文
            String userPrompt = String.format(
                "知识库内容：\n%s\n\n用户问题：%s",
                context,
                question
            );
            
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messagesList.add(userMessage);

            return remoteChat(messagesList);
        }
        
        // 如果Ollama服务不可用且在检查间隔内，直接返回离线回复
        if (!shouldTryConnectingToOllama()) {
            return getFallbackResponse(question);
        }

        try {
            System.out.println("[模式选择] 使用本地模式（Ollama）");
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
                    "model", ollamaModel,
                    "prompt", fullPrompt,
                    "stream", false
            );

            // 发送请求
            Map<String, Object> resp = ollamaClient.post()
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
                System.out.println("========== 使用Ollama本地模型生成回复成功 ==========");
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
        logger.info("历史消息：" + conversationContext);
        // 检查是否使用远程模式
        String currentChatMode = getCurrentChatMode();
        if ("remote".equals(currentChatMode)) {
            System.out.println("[模式选择] 使用远程模式（DeepSeek API）- 带上下文");
            logger.info("当前活动的聊天模式 = " + currentChatMode);
            
            // 将相关段落合并成一个上下文
            String context = relevantSegments.isEmpty()
                    ? "没有找到相关的知识库内容。"
                    : relevantSegments.stream()
                    .map(seg -> seg.trim())
                    .collect(Collectors.joining("\n\n"));

            // 解析已有的conversationContext并构建消息列表
            List<Map<String, String>> messagesList = new ArrayList<>();
            
            // 添加系统提示消息
            if (!systemPrompt.isEmpty()) {
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                
                // 在系统提示中加入必要的指令
                String enhancedSystemPrompt = systemPrompt;
                if (!preventHallucinationPrompt.isEmpty()) {
                    enhancedSystemPrompt += "\n\n" + preventHallucinationPrompt;
                }
                if (!citationPrompt.isEmpty()) {
                    enhancedSystemPrompt += "\n\n" + citationPrompt;
                }
                if (!formatInstruction.isEmpty()) {
                    enhancedSystemPrompt += "\n\n" + formatInstruction;
                }
                
                systemMessage.put("content", enhancedSystemPrompt);
                messagesList.add(systemMessage);
                logger.info("添加增强的系统提示" + systemMessage.get("content"));
            }
            
            // 解析历史对话并添加到消息列表
            if (conversationContext != null && !conversationContext.isEmpty()) {

                // 尝试从对话历史文本中提取对话
                // 这里假设对话历史的格式是"用户: xxx\n助手: xxx\n用户: xxx\n助手: xxx"
                String[] exchanges = conversationContext.split("\n");
                for (String exchange : exchanges) {
                    if (exchange.startsWith("用户: ")) {
                        Map<String, String> userMessage = new HashMap<>();
                        userMessage.put("role", "user");
                        userMessage.put("content", exchange.substring(4));
                        messagesList.add(userMessage);
                    } else if (exchange.startsWith("助手: ")) {
                        Map<String, String> assistantMessage = new HashMap<>();
                        assistantMessage.put("role", "assistant");
                        assistantMessage.put("content", exchange.substring(4));
                        messagesList.add(assistantMessage);
                    }
                }
                logger.info("添加历史对话，共 " + (messagesList.size() - 1) + " 条消息");
            }
            
            // 准备最后的用户问题，包含知识库上下文
            String finalUserQuery = String.format(
                "知识库内容：\n%s\n\n用户当前问题：%s",
                context,
                question
            );
            
            // 添加当前用户问题
            Map<String, String> currentUserMessage = new HashMap<>();
            currentUserMessage.put("role", "user");
            currentUserMessage.put("content", finalUserQuery);
            messagesList.add(currentUserMessage);
            
            logger.info("调用DeepSeek API生成回复，包含结构化对话上下文，消息总数: " + messagesList.size());
            return remoteChat(messagesList);
        }
        
        // 如果Ollama服务不可用且在检查间隔内，直接返回离线回复
        if (!shouldTryConnectingToOllama()) {
            return getFallbackResponse(question);
        }

        try {
            System.out.println("[模式选择] 使用本地模式（Ollama）- 带上下文");
            logger.info("当前活动的聊天模式 = " + currentChatMode);
            
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
                    "model", ollamaModel,
                    "prompt", fullPrompt,
                    "stream", false
            );

            // 发送请求
            Map<String, Object> resp = ollamaClient.post()
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
                System.out.println("========== 使用Ollama本地模型生成回复成功 ==========");
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
        // 检查是否使用远程模式
        String currentChatMode = getCurrentChatMode();
        if ("remote".equals(currentChatMode)) {
            System.out.println("[模式选择] 使用远程模式（DeepSeek API）- 简单回复");
            
            // 构建消息列表
            List<Map<String, String>> messagesList = new ArrayList<>();
            
            // 添加系统提示消息
            if (!systemPrompt.isEmpty()) {
                messagesList.add(Map.of(
                    "role", "system",
                    "content", systemPrompt
                ));
            }
            
            // 添加用户消息
            messagesList.add(Map.of(
                "role", "user",
                "content", userQuestion
            ));
            
            return remoteChat(messagesList);
        }
        
        // 如果Ollama服务不可用且在检查间隔内，直接返回离线回复
        if (!shouldTryConnectingToOllama()) {
            return getFallbackResponse(userQuestion);
        }

        try {
            // 构建更完善的提示词
//            String fullPrompt = String.format(
//                "你是一个AI助手，能与用户闲聊或者解答用户问题：\n\n用户说：%s\n\n助手回答：",
//                userQuestion
//            );

            logger.info("调用 LLM 生成简单回复，提示词长度: " + userQuestion.length());

            // 构造请求体
            Map<String, Object> req = Map.of(
                    "model", ollamaModel,
                    "prompt", userQuestion,
                    "stream", false
            );

            // 发送请求到Ollama
            Map<String, Object> resp = ollamaClient.post()
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
     * 使用结构化的消息历史生成回复
     * @param messageHistory 结构化的消息历史列表
     * @return 生成的回答
     */
    @SuppressWarnings("unchecked")
    public String generateResponse(List<Map<String, String>> messageHistory) {
        // 检查是否使用远程模式
        String currentChatMode = getCurrentChatMode();
        if ("remote".equals(currentChatMode)) {
            System.out.println("[模式选择] 使用远程模式（DeepSeek API）- 多轮对话");
            logger.info("调用DeepSeek API进行多轮对话，消息历史数量：" + messageHistory.size());
            
            // 直接使用提供的消息历史
            return remoteChat(messageHistory);
        } else {
            // 使用本地模式，将结构化消息转换为文本
            StringBuilder conversationText = new StringBuilder();
            
            // 跳过系统消息
            int startIndex = 0;
            if (!messageHistory.isEmpty() && "system".equals(messageHistory.get(0).get("role"))) {
                startIndex = 1;
            }
            
            // 构建对话文本
            for (int i = startIndex; i < messageHistory.size(); i++) {
                Map<String, String> message = messageHistory.get(i);
                String role = message.get("role");
                String content = message.get("content");
                
                if ("user".equals(role)) {
                    conversationText.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    conversationText.append("助手: ").append(content).append("\n");
                }
            }
            
            // 获取最后一条用户消息作为当前问题
            String currentQuestion = "";
            for (int i = messageHistory.size() - 1; i >= 0; i--) {
                Map<String, String> message = messageHistory.get(i);
                if ("user".equals(message.get("role"))) {
                    currentQuestion = message.get("content");
                    break;
                }
            }
            
            // 如果找不到用户消息，返回错误
            if (currentQuestion.isEmpty()) {
                return "错误：消息历史中没有用户问题";
            }
            
            logger.info("转换为文本的对话历史：\n" + conversationText.toString());
            
            // 调用Ollama API
            try {
                // 构造请求体
                Map<String, Object> req = Map.of(
                        "model", ollamaModel,
                        "prompt", conversationText.toString() + "\n助手: ",
                        "stream", false
                );

                // 发送请求到Ollama
                Map<String, Object> resp = ollamaClient.post()
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
                    logger.info("成功获取多轮对话回复，长度: " + response.length());
                    return response;
                }

                return "无法解析 LLM 返回的响应格式";
            } catch (WebClientRequestException e) {
                // 标记服务不可用
                ollamaServiceAvailable = false;
                lastCheckTime = System.currentTimeMillis();
                logger.severe("无法连接到Ollama服务: " + e.getMessage());
                return getFallbackResponse(currentQuestion);
            } catch (Exception e) {
                logger.severe("调用 LLM 服务失败: " + e.getMessage());
                e.printStackTrace();
                return "服务调用出错: " + e.getMessage();
            }
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

    /**
     * 调用DeepSeek API进行聊天
     * @param conversationHistory 对话历史记录
     * @return 生成的回答
     */
    private String remoteChat(List<Map<String, String>> conversationHistory) {
        try {
            logger.info("调用DeepSeek API，会话消息数量: " + conversationHistory.size());
            System.out.println("========== 使用DeepSeek远程API生成回复 ==========");
            
            // 构造DeepSeek API的请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", deepseekModel);
            requestBody.put("messages", conversationHistory);
            requestBody.put("temperature", deepseekTemperature);
            requestBody.put("max_tokens", deepseekMaxTokens);
            
            logger.info("DeepSeek请求参数 - 模型: " + deepseekModel);
            logger.info("DeepSeek请求参数 - 温度: " + deepseekTemperature);
            logger.info("DeepSeek请求参数 - 最大tokens: " + deepseekMaxTokens);
            
            // 打印请求URL信息
            logger.info("准备发送请求到DeepSeek API");
            
            // 发送请求到DeepSeek API
            Map<String, Object> response = deepseekClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(60))
                .block();
                
            if (response == null) {
                logger.severe("DeepSeek API未返回有效响应");
                return "DeepSeek API未返回有效响应";
            }
            
            // 记录完整响应
            logger.info("DeepSeek API完整响应: " + response);
            
            // 提取回复文本
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, String> message = (Map<String, String>) choice.get("message");
                if (message != null) {
                    String content = message.get("content");
                    if (content != null) {
                        logger.info("成功从DeepSeek API获取回复，长度: " + content.length());
                        System.out.println("========== DeepSeek远程API回复成功 ==========");
                        return content;
                    }
                }
            }
            
            logger.warning("无法从DeepSeek API响应中提取内容: " + response);
            return "无法解析DeepSeek API的响应格式";
        } catch (Exception e) {
            logger.severe("调用DeepSeek API失败: " + e.getMessage());
            e.printStackTrace();
            System.err.println("DeepSeek API错误: " + e.getMessage());
            return "调用DeepSeek API出错: " + e.getMessage();
        }
    }

    /**
     * 使用单个提示词调用DeepSeek API（简化版，用于兼容旧代码）
     * @param prompt 用户提示词
     * @return 生成的回答
     */
    private String remoteChat(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 添加系统提示消息
        if (!systemPrompt.isEmpty()) {
            messages.add(Map.of(
                "role", "system",
                "content", systemPrompt
            ));
        }
        
        // 添加用户消息
        messages.add(Map.of(
            "role", "user",
            "content", prompt
        ));
        
        return remoteChat(messages);
    }
}