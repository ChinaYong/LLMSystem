package com.example.chatbot.controller;

import com.example.chatbot.model.Chat;
import com.example.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天记录 CRUD 接口
 */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /** 创建聊天记录 */
    @PostMapping
    public ResponseEntity<Chat> createChat(@RequestBody Chat chat) {
        Chat saved = chatService.createChat(chat);
        return ResponseEntity.ok(saved);
    }

    /** 查询所有聊天记录 */
    @GetMapping
    public ResponseEntity<List<Chat>> getAllChats() {
        return ResponseEntity.ok(chatService.getAllChats());
    }

    /** 根据 ID 查询聊天记录 */
    @GetMapping("/{id}")
    public ResponseEntity<Chat> getChatById(@PathVariable Long id) {
        return chatService.getChatById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 更新聊天记录 */
    @PutMapping("/{id}")
    public ResponseEntity<Chat> updateChat(
            @PathVariable Long id,
            @RequestBody Chat chat) {
        return chatService.updateChat(id, chat)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 删除聊天记录 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long id) {
        chatService.deleteChat(id);
        return ResponseEntity.noContent().build();
    }
    
    /** 获取用户的所有会话ID */
    @GetMapping("/user/{userId}/sessions")
    public ResponseEntity<List<String>> getUserSessionIds(@PathVariable Long userId) {
        List<String> sessionIds = chatService.getSessionIdsByUserId(userId);
        return ResponseEntity.ok(sessionIds);
    }
    
    /** 获取用户的历史对话，按会话ID分组 */
    @GetMapping("/user/{userId}/history")
    public ResponseEntity<Map<String, List<Chat>>> getUserChatHistory(@PathVariable Long userId) {
        Map<String, List<Chat>> chatHistory = chatService.getUserChatHistoryBySession(userId);
        return ResponseEntity.ok(chatHistory);
    }
    
    /** 获取用户特定会话的对话历史 */
    @GetMapping("/user/{userId}/session/{sessionId}")
    public ResponseEntity<List<Chat>> getUserSessionChats(
            @PathVariable Long userId,
            @PathVariable String sessionId) {
        List<Chat> sessionChats = chatService.getChatsByUserIdAndSessionId(userId, sessionId);
        return ResponseEntity.ok(sessionChats);
    }
}
