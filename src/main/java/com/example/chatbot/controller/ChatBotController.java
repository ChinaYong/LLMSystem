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
     * @param req 包含字段: question(用户问题)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnswerResponse> chat(@RequestBody QuestionRequest req) {
        if (req.getQuestion() == null || req.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String answer = chatService.getAnswer(req.getQuestion());
        return ResponseEntity.ok(new AnswerResponse(answer));
    }

    // 请求 DTO
    public static class QuestionRequest {
        private String question;
        
        public String getQuestion() { 
            return question; 
        }
        
        public void setQuestion(String question) { 
            this.question = question; 
        }
    }

    // 响应 DTO
    public static class AnswerResponse {
        private String answer;
        
        public AnswerResponse() {}
        
        public AnswerResponse(String answer) { 
            this.answer = answer; 
        }
        
        public String getAnswer() { 
            return answer; 
        }
        
        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
}