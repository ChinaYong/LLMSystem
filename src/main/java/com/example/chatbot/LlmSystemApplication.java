package com.example.chatbot;

import com.example.chatbot.service.EmbeddingService;
import com.example.chatbot.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class LlmSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmSystemApplication.class, args);
    }

    @Bean
    public CommandLineRunner demoProcess(KnowledgeService ks, EmbeddingService es) {
        return args -> {
            // 演示用：启动时执行一些操作（可省略）
            System.out.println(">> 知识库服务就绪");
        };
    }

    /**
     * 启动时打印所有 @RequestMapping 信息，帮助排查哪些接口被注册了
     */
    @Bean
    public CommandLineRunner printAllMappings(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
        return args -> {
            System.out.println("==== 所有请求映射 ====");
            // 获取 URL 与控制器的映射关系
            Map<?, ?> map = mapping.getHandlerMethods();
            map.forEach((key, value) -> {
                // 打印 URL 路径和对应处理方法信息
                System.out.printf("路径: %s >> 方法: %s%n",
                        formatEndpoint(key),
                        value);
            });
            System.out.println("==== 映射列表完毕 ====");
        };
    }

    private String formatEndpoint(Object key) {
        if (key.toString().contains("specific=")) {
            return key.toString().replaceAll(".*\\{(.*)\\}.*", "$1")
                    .replace("[", "")
                    .replace("]", "")
                    .replace("specific=", "")
                    .lines()
                    .map(String::trim)
                    .collect(Collectors.joining(", "));
        }
        return key.toString();
    }
}