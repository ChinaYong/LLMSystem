package com.example.chatbot.controller;

import com.example.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 聊天接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatBotController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    /**
     * 用户提问接口
     * @param req 包含字段: question(用户问题), sessionId(会话ID)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnswerResponse> chat(@RequestBody QuestionRequest req) {
        if (req.getQuestion() == null || req.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // 使用传入的sessionId调用ChatService
        String answer = chatService.getAnswer(req.getQuestion(), req.getSessionId());
        
        // 返回响应，包含回答和会话ID
        return ResponseEntity.ok(new AnswerResponse(answer, req.getSessionId()));
    }

    // 请求 DTO
    public static class QuestionRequest {
        private String question;
        private String sessionId;  // 添加sessionId字段
        
        public String getQuestion() { 
            return question; 
        }
        
        public void setQuestion(String question) { 
            this.question = question; 
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    // 响应 DTO
    public static class AnswerResponse {
        private String answer;
        private String sessionId;  // 添加sessionId字段
        
        public AnswerResponse() {}
        
        public AnswerResponse(String answer) { 
            this.answer = answer;
        }
        
        public AnswerResponse(String answer, String sessionId) {
            this.answer = answer;
            this.sessionId = sessionId;
        }
        
        public String getAnswer() { 
            return answer; 
        }
        
        public void setAnswer(String answer) {
            this.answer = answer;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}