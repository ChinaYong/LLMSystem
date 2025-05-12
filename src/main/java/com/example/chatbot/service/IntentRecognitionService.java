package com.example.chatbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * 意图识别服务：识别用户问题的意图类型
 */
@Service
public class IntentRecognitionService {
    private static final Logger logger = Logger.getLogger(IntentRecognitionService.class.getName());

    /**
     * 意图类型枚举
     */
    public enum Intent {
        // 知识型问题 - 需要查询知识库
        KNOWLEDGE,

        // 闲聊型对话 - 如问候、感谢、告别等社交性质的交流
        CHIT_CHAT,

        // 系统功能 - 关于机器人自身或功能的问题
        SYSTEM,

        // 敏感内容 - 不适合回答的敏感话题
        SENSITIVE,

        // 不明确问题 - 问题模糊或难以理解
        UNCLEAR,

        // 超出范围 - 超出机器人能力范围的问题
        OUT_OF_SCOPE
    }

    @Autowired
    private LLMService llmService;

    /**
     * 使用LLM识别问题意图
     * @param question 用户问题
     * @return 识别出的意图
     */
    public Intent recognizeIntentWithLLM(String question) {
        // 构建提示词，让LLM进行意图分类
        String prompt = buildIntentPrompt(question);

        // 调用LLM服务进行分类
        String response = llmService.generateResponse(prompt);
        logger.info("LLM原始响应: " + response);

        // 从LLM响应中解析意图
        Intent intent = parseIntentFromResponse(response);
        logger.info("从LLM响应中提取到意图: " + intent);

        return intent;
    }

    /**
     * 构建用于意图识别的提示词
     */
    private String buildIntentPrompt(String question) {
        return "你是一个意图分类器。请分析用户问题，并将其归类为以下意图之一：\n" +
                "KNOWLEDGE：需要专业知识或查询知识库回答的问题，如'什么是Java?'、'公司的退货政策是什么?'\n" +
                "CHIT_CHAT：社交性质的问题，如问候('你好'、'早上好')、告别('再见'、'拜拜')、感谢('谢谢'、'谢谢你的帮助')\n" +
                "SYSTEM：关于你自己或系统功能的问题，如'你是谁?'、'你能做什么?'、'介绍一下自己'\n" +
                "SENSITIVE：包含政治、宗教、歧视、成人内容等敏感话题\n" +
                "UNCLEAR：问题模糊、不完整或难以理解\n" +
                "OUT_OF_SCOPE：超出你能力范围的问题，如需要实时数据、执行操作等\n\n" +
                "用户问题：\"" + question + "\"\n\n" +
                "仅回复对应的大写意图类别，不要包含其他任何文字。";
    }

    /**
     * 从LLM响应中解析出意图
     */
    private Intent parseIntentFromResponse(String response) {
        // 清理并标准化响应文本
        String cleanedResponse = response.trim().toUpperCase();

        // 尝试精确匹配意图关键词
        if (cleanedResponse.contains("KNOWLEDGE")) {
            return Intent.KNOWLEDGE;
        } else if (cleanedResponse.contains("CHIT_CHAT")) {
            return Intent.CHIT_CHAT;
        } else if (cleanedResponse.contains("SYSTEM")) {
            return Intent.SYSTEM;
        } else if (cleanedResponse.contains("SENSITIVE")) {
            return Intent.SENSITIVE;
        } else if (cleanedResponse.contains("UNCLEAR")) {
            return Intent.UNCLEAR;
        } else if (cleanedResponse.contains("OUT_OF_SCOPE")) {
            return Intent.OUT_OF_SCOPE;
        }

        // 无法识别时默认为知识型问题
        logger.warning("无法从响应中识别出意图: " + cleanedResponse + "，默认为 KNOWLEDGE");
        return Intent.KNOWLEDGE;
    }

    /**
     * 使用规则进行意图识别(备用方法)
     * @param question 用户问题
     * @return 识别出的意图
     */
    public Intent recognizeIntent(String question) {
        // 转换为小写以便匹配
        String lowercaseQuestion = question.toLowerCase();

        // 问候词模式匹配
        if (matchesPattern(lowercaseQuestion, "你好", "早上好", "晚上好", "下午好", "嗨", "hello", "hi", "hey")) {
            return Intent.CHIT_CHAT;
        }

        // 告别词模式匹配
        if (matchesPattern(lowercaseQuestion, "再见", "拜拜", "bye", "goodbye", "see you")) {
            return Intent.CHIT_CHAT;
        }

        // 感谢词模式匹配
        if (matchesPattern(lowercaseQuestion, "谢谢", "感谢", "thanks", "thank you", "thx")) {
            return Intent.CHIT_CHAT;
        }

        // 系统功能询问
        if (matchesPattern(lowercaseQuestion, "你是谁", "介绍自己", "你能做什么", "你的功能", "about you", "你叫什么", "你的名字")) {
            return Intent.SYSTEM;
        }

        // 默认为知识型问题
        return Intent.KNOWLEDGE;
    }

    /**
     * 辅助方法：检查文本是否包含任一模式
     */
    private boolean matchesPattern(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}