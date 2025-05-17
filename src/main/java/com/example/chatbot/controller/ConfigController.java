package com.example.chatbot.controller;

import com.example.chatbot.config.PromptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 配置相关的控制器，提供查看当前配置的接口
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@RefreshScope
public class ConfigController {

    private static final Logger logger = Logger.getLogger(ConfigController.class.getName());
    private final PromptProperties promptProperties;
    private final Environment environment;
    
    // 当前会话的聊天模式（不保存在应用属性中，而是动态维护）
    private String currentChatMode;
    
    /**
     * 获取当前的提示词配置
     */
    @GetMapping("/prompt-settings")
    public Map<String, String> getPromptSettings() {
        Map<String, String> result = new HashMap<>();
        result.put("system", promptProperties.getSystem());
        result.put("fallback", promptProperties.getFallback());
        return result;
    }
    
    /**
     * 获取当前的聊天模式
     */
    @GetMapping("/chat-mode")
    public Map<String, String> getChatMode() {
        String mode = currentChatMode != null ? 
            currentChatMode : environment.getProperty("chat.mode", "local");
        Map<String, String> result = new HashMap<>();
        result.put("mode", mode);
        System.out.println("当前聊天模式: " + mode);
        return result;
    }
    
    /**
     * 更新聊天模式
     */
    @PostMapping("/chat-mode")
    public ResponseEntity<String> updateChatMode(@RequestBody Map<String, String> request) {
        String mode = request.get("mode");
        if (mode == null || (!mode.equals("local") && !mode.equals("remote"))) {
            return ResponseEntity.badRequest().body("模式必须为 'local' 或 'remote'");
        }
        
        // 保存当前会话的聊天模式
        this.currentChatMode = mode;
        
        logger.info("聊天模式已更新为: " + mode);
        System.out.println("聊天模式已更新为: " + (mode.equals("remote") ? "DeepSeek API" : "本地Ollama"));
        return ResponseEntity.ok("聊天模式已更新为: " + mode);
    }
}