package com.example.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ollama.api.url:http://localhost:11434}")
    private String ollamaUrl;

    @Bean
    public WebClient ollamaClient() {
        return WebClient.builder()
                .baseUrl(ollamaUrl)
                .build();
    }
}
