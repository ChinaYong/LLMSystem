package com.example.chatbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 聊天机器人提示词配置，可在 application.properties 中编写，
 * 支持通过 Spring Cloud 的 RefreshScope 进行实时刷新。
 */
@Component
@ConfigurationProperties(prefix = "chatbot.prompt")
@RefreshScope
@Data
public class PromptProperties {
    /** 系统级设定（人格、角色、语气等） */
    private String system = "你是一个AI助理,优先使用知识库的内容回答。如果用户问你问题，不涉及政治、色情等敏感内容，那么直接回复。如果用户咨询可能引起纠纷的法律、医学等内容，要强调仅供参考。" +
            "你还可能会从Prompt中收到与用户的历史对话，仅在必要时参考历史对话，比如用户问题需要结合历史对话回答，其余情况一律无视历史对话。";

    /** 当知识库为空或不足时的兜底回答 */
    private String fallback;
    
    /** 防止幻觉的额外指令 */
    private String preventHallucination;
    
    /** 知识库引用提示 */
    private String citation;
    
    /** 回答格式指导 */
    private String formatInstruction;
}