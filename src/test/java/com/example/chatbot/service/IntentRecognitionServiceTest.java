package com.example.chatbot.service;

import com.example.chatbot.service.IntentRecognitionService.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 意图识别服务测试类
 */
public class IntentRecognitionServiceTest {

    @Mock
    private LLMService llmService;

    @InjectMocks
    private IntentRecognitionService intentRecognitionService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testRecognizeIntent_ChitChat_Greeting() {
        // 测试问候意图
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("你好"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("早上好啊"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("嗨，最近怎么样？"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("Hello there!"));
    }

    @Test
    public void testRecognizeIntent_ChitChat_Farewell() {
        // 测试告别意图
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("再见"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("拜拜，下次聊"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("Goodbye, see you later"));
    }

    @Test
    public void testRecognizeIntent_ChitChat_Thanks() {
        // 测试感谢意图
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("谢谢你的帮助"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("非常感谢"));
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntent("Thank you so much"));
    }

    @Test
    public void testRecognizeIntent_System_Help() {
        // 测试系统帮助请求意图
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntent("我需要帮助"));
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntent("怎么用这个系统？"));
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntent("Can you help me?"));
    }

    @Test
    public void testRecognizeIntent_Knowledge() {
        // 测试知识查询意图
        assertEquals(Intent.KNOWLEDGE, intentRecognitionService.recognizeIntent("什么是人工智能？"));
        assertEquals(Intent.KNOWLEDGE, intentRecognitionService.recognizeIntent("如何学习编程？"));
        assertEquals(Intent.KNOWLEDGE, intentRecognitionService.recognizeIntent("为什么天空是蓝色的？"));
    }

    @Test
    public void testRecognizeIntent_System_Identity() {
        // 测试系统身份询问
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntent("你是谁"));
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntent("介绍一下自己"));
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntent("你叫什么名字"));
    }

    @Test
    public void testRecognizeIntent_Unknown() {
        // 测试未知意图（默认为KNOWLEDGE）
        assertEquals(Intent.KNOWLEDGE, intentRecognitionService.recognizeIntent("xyz123"));
        assertEquals(Intent.KNOWLEDGE, intentRecognitionService.recognizeIntent("随机文本"));
    }

    @Test
    public void testRecognizeIntentWithLLM() {
        // 模拟LLM返回的意图
        when(llmService.generateResponse(anyString())).thenReturn("CHIT_CHAT");

        // 测试LLM意图识别
        assertEquals(Intent.CHIT_CHAT, intentRecognitionService.recognizeIntentWithLLM("你好吗？"));

        // 模拟LLM返回系统意图
        when(llmService.generateResponse(anyString())).thenReturn("SYSTEM");
        assertEquals(Intent.SYSTEM, intentRecognitionService.recognizeIntentWithLLM("你是谁？"));

        // 模拟LLM返回无效意图，应该默认为知识型问题
        when(llmService.generateResponse(anyString())).thenReturn("INVALID_INTENT");
        assertEquals(Intent.KNOWLEDGE, intentRecognitionService.recognizeIntentWithLLM("随机问题"));
    }
}