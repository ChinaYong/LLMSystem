package com.example.chatbot.controller;

import com.example.chatbot.config.PromptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置相关的控制器，提供查看当前配置的接口
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final PromptProperties promptProperties;

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
}