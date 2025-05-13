package com.example.chatbot.service;

// 导入需要的类
import com.example.chatbot.model.Chat;  // 用于存储聊天记录的模型类
import com.example.chatbot.model.User;  // 添加User模型类的导入
import com.example.chatbot.repository.ChatRepository;  // 用于数据库操作的仓库接口
import com.example.chatbot.repository.UserRepository;  // 添加UserRepository的导入
import com.example.chatbot.service.IntentRecognitionService.Intent;  // 意图类型的枚举
import org.springframework.beans.factory.annotation.Autowired;  // 自动注入依赖
import org.springframework.security.core.Authentication;  // 添加Authentication的导入
import org.springframework.security.core.context.SecurityContextHolder;  // 添加SecurityContextHolder的导入
import org.springframework.stereotype.Service;  // 标记这是一个服务类

import java.time.LocalDateTime;  // 日期时间类
import java.time.LocalTime;  // 时间类
import java.util.*;  // 集合框架
import java.util.logging.Logger;  // 日志记录
import java.util.stream.Collectors;  // 用于流操作
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

// @Service 注解表明这是一个Spring服务组件，Spring会自动管理它的生命周期
@Service
public class ChatService {

    // 创建日志记录器
    private static final Logger logger = Logger.getLogger(ChatService.class.getName());

    // 创建一个用于存储会话状态的内存缓存
    // 外层Map的键是会话ID，值是内部Map
    // 内部Map存储各种会话相关的状态信息
    private final Map<String, Map<String, Object>> sessionStates = new HashMap<>();

    // @Autowired 注解让Spring自动注入这些依赖
    // ChatRepository用于操作聊天记录数据库
    @Autowired
    private ChatRepository chatRepository;
    
    // 添加UserRepository的注入
    @Autowired
    private UserRepository userRepository;

    // LLMService用于与大型语言模型交互
    @Autowired
    private LLMService llmService;

    // EmbeddingService用于生成和查询文本的向量嵌入
    @Autowired
    private EmbeddingService embeddingService;

    // IntentRecognitionService用于识别用户问题的意图
    @Autowired
    private IntentRecognitionService intentRecognitionService;

    /**
     * 创建聊天记录
     * @param chat 聊天对象
     * @return 保存后的聊天对象（包含ID等）
     */
    public Chat createChat(Chat chat) {
        // 如果未设置创建时间，则设置为当前时间
        if (chat.getCreatedAt() == null) {
            chat.setCreatedAt(LocalDateTime.now());
        }
        // 保存到数据库并返回
        return chatRepository.save(chat);
    }

    /**
     * 获取所有聊天记录
     * @return 所有聊天记录的列表
     */
    public List<Chat> getAllChats() {
        // 直接调用仓库方法查询所有记录
        return chatRepository.findAll();
    }

    /**
     * 根据ID获取特定的聊天记录
     * @param id 聊天记录ID
     * @return 包含聊天记录的Optional对象（可能为空）
     */
    public Optional<Chat> getChatById(Long id) {
        // 调用仓库方法根据ID查询
        return chatRepository.findById(id);
    }

    /**
     * 更新聊天记录
     * @param id 要更新的聊天记录ID
     * @param chat 包含更新内容的聊天对象
     * @return 更新后的聊天对象（如果存在）
     */
    public Optional<Chat> updateChat(Long id, Chat chat) {
        // 先查找是否存在该ID的记录
        return chatRepository.findById(id)
                .map(existingChat -> {
                    // 如果存在，则更新可修改字段

                    // 仅当新值不为null时才更新userId
                    if (chat.getUserId() != null) {
                        existingChat.setUserId(chat.getUserId());
                    }
                    // 仅当新值不为null时才更新问题
                    if (chat.getQuestion() != null) {
                        existingChat.setQuestion(chat.getQuestion());
                    }
                    // 仅当新值不为null时才更新回答
                    if (chat.getAnswer() != null) {
                        existingChat.setAnswer(chat.getAnswer());
                    }
                    // 设置更新时间为当前时间
                    existingChat.setUpdatedAt(LocalDateTime.now());

                    // 保存并返回更新后的实体
                    return chatRepository.save(existingChat);
                });
    }

    /**
     * 删除聊天记录
     * @param id 要删除的聊天记录ID
     */
    public void deleteChat(Long id) {
        // 调用仓库方法删除指定ID的记录
        chatRepository.deleteById(id);
    }
    
    /**
     * 获取用户的所有会话ID
     * @param userId 用户ID
     * @return 该用户的所有会话ID列表
     */
    public List<String> getSessionIdsByUserId(Long userId) {
        return chatRepository.findDistinctSessionIdsByUserId(userId);
    }
    
    /**
     * 获取用户的历史对话，按会话ID分组
     * @param userId 用户ID
     * @return 按会话ID分组的聊天记录Map
     */
    public Map<String, List<Chat>> getUserChatHistoryBySession(Long userId) {
        List<Chat> allUserChats = chatRepository.findByUserIdOrderBySessionId(userId);
        Map<String, List<Chat>> chatsBySession = new HashMap<>();
        
        // 按sessionId分组
        for (Chat chat : allUserChats) {
            String sessionId = chat.getSessionId();
            if (!chatsBySession.containsKey(sessionId)) {
                chatsBySession.put(sessionId, new ArrayList<>());
            }
            chatsBySession.get(sessionId).add(chat);
        }
        
        return chatsBySession;
    }
    
    /**
     * 获取用户特定会话的对话历史
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 该会话的所有聊天记录
     */
    public List<Chat> getChatsByUserIdAndSessionId(Long userId, String sessionId) {
        List<Chat> allChats = chatRepository.findBySessionId(sessionId);
        return allChats.stream()
                .filter(chat -> chat.getUserId().equals(userId))
                .sorted(Comparator.comparing(Chat::getCreatedAt))
                .collect(Collectors.toList());
    }

    /**
     * 获取问题的回答
     * @param question 用户的问题
     * @param sessionId 会话ID，用于追踪上下文，如果为null则创建新会话
     * @return 生成的回答
     */
    public String getAnswer(String question, String sessionId) {
        // 如果没有会话ID，创建一个新的
        if (sessionId == null || sessionId.isEmpty()) {
            // 生成一个随机UUID作为会话ID
            sessionId = UUID.randomUUID().toString();
            // 初始化新会话
            initializeSession(sessionId);
        }

        try {
            // 获取或初始化会话状态
            Map<String, Object> sessionState = getOrCreateSessionState(sessionId);

            // 更新会话状态 - 记录本次问题
            // 从会话状态中获取历史记录，如果不存在则创建新的列表
            List<String> history = (List<String>) sessionState.getOrDefault("history", new ArrayList<String>());
            // 添加当前问题到历史记录
            history.add("Q: " + question);
            // 更新会话状态中的历史记录
            sessionState.put("history", history);

            // 更新会话状态 - 记录问题时间
            sessionState.put("lastActivity", LocalDateTime.now());

            // 1. 识别用户问题的意图
            Intent intent = intentRecognitionService.recognizeIntentWithLLM(question);
            // 记录识别到的意图
            logger.info("用户问题: '" + question + "' 识别到的意图: " + intent);

            // 记录意图到会话状态
            sessionState.put("lastIntent", intent);

            // 2. 根据不同意图类型处理
            String answer;
            // 使用switch语句根据不同的意图类型调用不同的处理方法
//            switch (intent) {
//                case CHIT_CHAT:
//                    // 处理闲聊类型的问题
//                    answer = handleChitChat(question, sessionState);
//                    break;
//                case SYSTEM:
//                    // 处理系统相关的问题
//                    answer = handleSystemQuestion(question, sessionState);
//                    break;
//                case SENSITIVE:
//                    // 处理敏感内容
//                    answer = handleSensitiveContent(question, sessionState);
//                    break;
//                case UNCLEAR:
//                    // 处理不明确的问题
//                    answer = handleUnclearQuestion(question, sessionState);
//                    break;
//                case OUT_OF_SCOPE:
//                    // 处理超出范围的问题
//                    answer = handleOutOfScopeQuestion(question, sessionState);
//                    break;
//                case KNOWLEDGE:
//                default:
//                    // 对于知识型问题，使用知识库检索
//                    answer = handleKnowledgeQuery(question, sessionState);
//                    break;
//            }
            switch (intent) {
                case ACCEPT:
                    answer = handleKnowledgeQuery(question, sessionState);
                    break;
                case REFUSE, OUT_OF_SCOPE:
                    answer = handleRefuseQuestion(question, sessionState);
                    break;
                case SWITCH:
                    answer = "正常转接人工客服，请稍等...";
                    break;
                default:
                    answer = handleRefuseQuestion(question, sessionState);
                    break;
            }

            // 更新会话状态 - 记录本次回答
            history.add("A: " + answer);

            // 保存聊天记录到数据库
            saveChat(question, answer, sessionId);

            // 返回生成的回答
            return answer;
        } catch (Exception e) {
            // 记录错误日志
            logger.severe("处理问题时发生错误: " + e.getMessage());
            // 打印完整的错误堆栈
            e.printStackTrace();
            // 返回友好的错误消息
            return "抱歉，处理您的问题时出现了错误。";
        }
    }

    /**
     * 为兼容现有接口，不带sessionId的重载方法
     * @param question 用户的问题
     * @return 生成的回答
     */
    public String getAnswer(String question) {
        // 调用带sessionId参数的方法，但传入null
        return getAnswer(question, null);
    }
    
    /**
     * 获取当前登录用户的ID
     * @return 用户ID，如果未登录则返回null
     */
    private Long getCurrentUserId() {
        try {
            // 从Spring Security上下文中获取当前认证信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                    !"anonymousUser".equals(authentication.getPrincipal())) {
                // 获取用户名
                String username = authentication.getName();
                // 查询用户
                User user = userRepository.findByUsername(username);
                if (user != null) {
                    return user.getId();
                }
            }
        } catch (Exception e) {
            logger.warning("获取当前用户ID时发生错误: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取或创建会话状态
     * @param sessionId 会话ID
     * @return 对应会话ID的状态Map
     */
    private Map<String, Object> getOrCreateSessionState(String sessionId) {
        // 检查会话状态Map中是否已存在该会话ID
        if (!sessionStates.containsKey(sessionId)) {
            // 如果不存在，初始化一个新会话
            initializeSession(sessionId);
        }
        // 返回该会话ID对应的状态Map
        return sessionStates.get(sessionId);
    }

    /**
     * 初始化新会话
     * @param sessionId 会话ID
     */
    private void initializeSession(String sessionId) {
        // 创建一个新的Map来存储会话状态
        Map<String, Object> state = new HashMap<>();
        // 记录会话创建时间
        state.put("createdAt", LocalDateTime.now());
        // 记录最后活动时间
        state.put("lastActivity", LocalDateTime.now());
        // 初始化空的历史记录列表
        state.put("history", new ArrayList<String>());
        // 初始化问候计数器
        state.put("greetingCount", 0);
        // 初始化投诉计数器
        state.put("complaintCount", 0);
        // 将新创建的状态Map添加到会话状态Map中
        sessionStates.put(sessionId, state);
    }

    /**
     * 保存聊天记录到数据库
     * @param question 用户问题
     * @param answer 系统回答
     * @param sessionId 会话ID
     */
    private void saveChat(String question, String answer, String sessionId) {
        // 创建一个新的Chat对象
        Chat chat = new Chat();
        // 设置问题
        chat.setQuestion(question);
        // 设置回答
        chat.setAnswer(answer);
        // 设置会话ID
        chat.setSessionId(sessionId);
        // 设置创建时间
        chat.setCreatedAt(LocalDateTime.now());
        
        // 尝试获取当前登录用户的ID
        Long userId = getCurrentUserId();
        if (userId != null) {
            chat.setUserId(userId);
        }
        
        // 保存到数据库
        chatRepository.save(chat);
    }

    /**
     * 处理问候类型的问题
     * @param question 用户问题
     * @param sessionState 会话状态
     * @return 生成的回答
     */
    private String handleGreeting(String question, Map<String, Object> sessionState) {
        // 增加问候计数
        int greetingCount = (int) sessionState.getOrDefault("greetingCount", 0);
        // 更新计数值到会话状态
        sessionState.put("greetingCount", greetingCount + 1);

        // 获取当前时段
        LocalTime now = LocalTime.now();
        String timeOfDay;

        // 根据当前时间确定是早上、下午还是晚上
        if (now.isBefore(LocalTime.of(12, 0))) {
            timeOfDay = "早上";
        } else if (now.isBefore(LocalTime.of(18, 0))) {
            timeOfDay = "下午";
        } else {
            timeOfDay = "晚上";
        }

        // 根据问候次数和时段生成不同的问候语
        if (greetingCount <= 1) {
            // 第一次问候，使用标准问候语
            String[] greetings = {
                    timeOfDay + "好！有什么我可以帮助你的吗？",
                    timeOfDay + "好！很高兴为您服务。",
                    "嗨，" + timeOfDay + "好！请问有什么我可以协助您的？"
            };
            // 随机选择一个问候语返回
            return greetings[(int) (Math.random() * greetings.length)];
        } else {
            // 重复问候，给出不同回应
            String[] repeatedGreetings = {
                    "我们已经打过招呼了。有什么可以帮您的吗？",
                    "您好！请问有什么具体问题需要解答？",
                    "我在这里。请问有什么我能做的？"
            };
            // 随机选择一个重复问候回应返回
            return repeatedGreetings[(int) (Math.random() * repeatedGreetings.length)];
        }
    }

    /**
     * 处理告别类型的问题
     * @param question 用户问题
     * @param sessionState 会话状态
     * @return 生成的回答
     */
    private String handleFarewell(String question, Map<String, Object> sessionState) {
        // 标记会话可能结束
        sessionState.put("farewellSent", true);

        // 告别语数组
        String[] farewells = {
                "再见！如果有其他问题，随时回来咨询。",
                "下次见！祝您一切顺利。",
                "再会！有需要随时找我。"
        };
        // 随机选择一个告别语返回
        return farewells[(int) (Math.random() * farewells.length)];
    }

    /**
     * 处理感谢类型的问题
     * @param question 用户问题
     * @param sessionState 会话状态
     * @return 生成的回答
     */
    private String handleThanks(String question, Map<String, Object> sessionState) {
        // 分析之前有没有帮助过用户
        List<String> history = (List<String>) sessionState.getOrDefault("history", new ArrayList<String>());
        // 判断是否有实质性对话（至少有3轮对话）
        boolean hasProvided = history.size() > 2;

        if (hasProvided) {
            // 如果有实质性帮助，回复更热情
            String[] responses = {
                    "不客气！很高兴能帮到您。",
                    "您太客气了，这是我的荣幸。",
                    "不用谢！如果还有其他问题，随时告诉我。"
            };
            // 随机选择一个回应返回
            return responses[(int) (Math.random() * responses.length)];
        } else {
            // 用户可能没有得到实质性帮助就感谢
            String[] simpleResponses = {
                    "不用谢！有什么具体问题我可以帮您解答吗？",
                    "很高兴能帮到您。您有什么特定问题需要咨询吗？",
                    "不客气！请问您需要什么帮助？"
            };
            // 随机选择一个简单回应返回
            return simpleResponses[(int) (Math.random() * simpleResponses.length)];
        }
    }

    /**
     * 处理帮助请求类型的问题
     * @param question 用户问题
     * @param sessionState 会话状态
     * @return 生成的回答
     */
    private String handleHelp(String question, Map<String, Object> sessionState) {
        // 返回系统功能介绍
        return "我是一个AI助手，可以回答您的问题、提供信息和帮助解决问题。您可以：\n\n" +
                "1. 询问任何专业知识问题\n" +
                "2. 寻求技术支持或使用指导\n" +
                "3. 提供反馈或建议\n" +
                "4. 提交投诉或问题报告\n\n" +
                "请告诉我您具体需要什么帮助，我会尽力提供准确的回答。";
    }

    /**
     * 处理澄清请求类型的问题
     * @param question 用户问题
     * @param sessionState 会话状态
     * @return 生成的回答
     */
    private String handleClarification(String question, Map<String, Object> sessionState) {
        // 尝试查找上一次的回答以提供澄清
        List<String> history = (List<String>) sessionState.getOrDefault("history", new ArrayList<String>());
        String lastAnswer = "";
        
        // 从历史记录末尾向前查找，找到最近的一条系统回答
        for (int i = history.size() - 1; i >= 0; i--) {
            String entry = history.get(i);
            if (entry.startsWith("A: ")) {
                // 去掉 "A: " 前缀，获取实际回答内容
                lastAnswer = entry.substring(3);
                break;
            }
        }

        if (!lastAnswer.isEmpty()) {
            // 用上一次的回答作为上下文，请求LLM提供澄清
            // 构建提示词，引导LLM提供基于上一次回答的澄清
            String clarificationPrompt = String.format(
                    "用户对我之前的回答请求澄清。\n" +
                            "我的上一次回答是: %s\n" +
                            "用户的请求是: %s\n" +
                            "请基于我上一次的回答，提供更清晰、更详细的解释。",
                    lastAnswer, question
            );
            // 调用LLM生成澄清回答
            return llmService.generateResponse(clarificationPrompt);
        } else {
            // 没有前文上下文，请用户提供更多细节
            return "抱歉，我可能无法理解您的问题。请提供更多细节，或者用不同的方式表述您的问题，我会尽力帮助您。";
        }
    }

    /**
     * 处理投诉类型的问题
     * @param question 用户问题
     * @param sessionState 会话状态
     * @return 生成的回答
     */
    private String handleComplaint(String question, Map<String, Object> sessionState) {
        // 跟踪投诉次数
        int complaintCount = (int) sessionState.getOrDefault("complaintCount", 0);
        // 更新投诉计数到会话状态
        sessionState.put("complaintCount", complaintCount + 1);

        if (complaintCount >= 2) {
            // 多次投诉，提供转人工选项
            return "非常抱歉您遇到了持续的问题。我们可以为您转接人工客服以获取进一步帮助。请问您希望转接人工客服吗？";
        } else {
            return "非常抱歉给您带来不便。我们非常重视您的反馈，会努力改进我们的服务。请详细描述您遇到的问题，以便我们能更好地帮助您解决。如果需要，我也可以为您转接人工客服。";
        }
    }

    /**
     * 处理反馈类型的问题
     */
    private String handleFeedback(String question, Map<String, Object> sessionState) {
        // 区分积极反馈和消极反馈
        if (question.contains("好") || question.contains("赞") || question.contains("优秀") ||
                question.contains("good") || question.contains("great") || question.contains("excellent")) {
            return "非常感谢您的积极评价！我们会继续努力提供优质服务。如果您有更多建议，也欢迎随时提出。";
        } else {
            return "感谢您的反馈！您的意见对我们非常重要，我们会认真考虑您的建议，不断改进我们的服务。如果您有更具体的建议，也请告诉我们。";
        }
    }

    /**
     * 处理信息查询类型的问题
     */
    private String handleInformationQuery(String question, Map<String, Object> sessionState) {
        // 1. 查询相关知识片段
        List<String> relevantSegments = embeddingService.findRelevantSegments(question, 3);

        // 2. 获取历史对话作为上下文
        List<String> history = (List<String>) sessionState.getOrDefault("history", new ArrayList<String>());
        String conversationContext = "";

        // 只取最近的3轮对话作为上下文
        int startIndex = Math.max(0, history.size() - 6); // 3轮问答共6条记录
        if (startIndex < history.size()) {
            conversationContext = String.join("\n", history.subList(startIndex, history.size() - 1));  // 不包括当前问题
            if (!conversationContext.isEmpty()) {
                conversationContext = "对话历史：\n" + conversationContext + "\n\n";
            }
        }

        // 3. 调用 LLM 服务生成回答，加入对话历史作为上下文
        return llmService.generateAnswerWithContext(question, relevantSegments, conversationContext);
    }

    /**
     * 处理闲聊类型的问题（问候、感谢、告别等）
     */
    private String handleChitChat(String question, Map<String, Object> sessionState) {
        String lowercaseQuestion = question.toLowerCase();
        
        // 问候处理
        if (lowercaseQuestion.contains("你好") || lowercaseQuestion.contains("早上") || 
                lowercaseQuestion.contains("下午") || lowercaseQuestion.contains("晚上") || 
                lowercaseQuestion.contains("嗨") || lowercaseQuestion.contains("hi") || 
                lowercaseQuestion.contains("hello")) {
            return handleGreeting(question, sessionState);
        }
        
        // 告别处理
        if (lowercaseQuestion.contains("再见") || lowercaseQuestion.contains("拜拜") || 
                lowercaseQuestion.contains("bye") || lowercaseQuestion.contains("goodbye")) {
            return handleFarewell(question, sessionState);
        }
        
        // 感谢处理
        if (lowercaseQuestion.contains("谢谢") || lowercaseQuestion.contains("感谢") || 
                lowercaseQuestion.contains("thanks") || lowercaseQuestion.contains("thank")) {
            return handleThanks(question, sessionState);
        }
        
        // 其他社交对话
        String[] chitChatResponses = {
            "我很好，谢谢关心！您有什么我可以帮助的吗？",
            "很高兴与您交流！有什么具体问题吗？",
            "我随时准备为您提供帮助！"
        };
        return chitChatResponses[(int)(Math.random() * chitChatResponses.length)];
    }

    /**
     * 处理系统相关问题（关于机器人自身或功能）
     */
    private String handleSystemQuestion(String question, Map<String, Object> sessionState) {
        String lowercaseQuestion = question.toLowerCase();
        
        // 关于机器人身份的问题
        if (lowercaseQuestion.contains("你是谁") || lowercaseQuestion.contains("介绍自己") || 
                lowercaseQuestion.contains("你叫什么") || lowercaseQuestion.contains("你的名字")) {
            return "我是ZZY，一个AI助手，旨在提供信息和回答问题。我基于人工智能技术开发，能够理解并回答各种问题，访问知识库获取信息，并尽力为您提供有用的回答。";
        }
        
        // 关于机器人功能的问题
        if (lowercaseQuestion.contains("你能做什么") || lowercaseQuestion.contains("你的功能") || 
                lowercaseQuestion.contains("help") || lowercaseQuestion.contains("帮助")) {
            return handleHelp(question, sessionState);
        }
        
        // 其他系统相关问题
        return "我是ZZY，一个AI助手，可以回答您的问题、提供信息和帮助解决问题。有什么我可以帮您的吗？";
    }

    /**
     * 处理敏感内容
     */
    private String handleSensitiveContent(String question, Map<String, Object> sessionState) {
        String[] responses = {
            "抱歉，我无法讨论这类敏感话题。有其他我可以帮助您的问题吗？",
            "这个话题超出了我的服务范围。您可以问我一些其他问题。",
            "作为AI助手，我被设计为不讨论敏感或有争议的话题。有其他我可以帮助您的事情吗？"
        };
        return responses[(int)(Math.random() * responses.length)];
    }

    /**
     * 处理拒绝回复的问题
     */
    private String handleRefuseQuestion(String question, Map<String, Object> sessionState) {
        String[] responses = {
            "抱歉，您的提问不符合回复要求，请换个问题",
            "问题违反条例，拒绝服务",
            "拒绝回复"
        };
        return responses[(int)(Math.random() * responses.length)];
    }

    /**
     * 处理超出范围的问题
     */
    private String handleOutOfScopeQuestion(String question, Map<String, Object> sessionState) {
        String[] responses = {
            "抱歉，这个问题超出了我的能力范围。我无法提供实时数据或执行具体操作。",
            "作为AI助手，我无法执行这类操作。有其他我可以帮助您的问题吗？",
            "这超出了我的功能范围。我主要擅长回答问题和提供信息，而不能执行具体任务。"
        };
        return responses[(int)(Math.random() * responses.length)];
    }

    /**
     * 处理知识型查询
     */
    private String handleKnowledgeQuery(String question, Map<String, Object> sessionState) {
        // 1. 查询相关知识片段
        List<String> relevantSegments = embeddingService.findRelevantSegments(question, 3);

        // 2. 获取历史对话作为上下文
        List<String> history = (List<String>) sessionState.getOrDefault("history", new ArrayList<String>());
        String conversationContext = "";

        // 只取最近的3轮对话作为上下文
        int startIndex = Math.max(0, history.size() - 6); // 3轮问答共6条记录
        if (startIndex < history.size()) {
            conversationContext = String.join("\n", history.subList(startIndex, history.size() - 1));  // 不包括当前问题
            if (!conversationContext.isEmpty()) {
                conversationContext = "对话历史：\n" + conversationContext + "\n\n";
            }
        }

        // 3. 如果没有找到相关知识片段附加提示信息
        if (relevantSegments.isEmpty()) {
//            return "抱歉，我的知识库中没有与您问题相关的信息。您可以尝试换一种方式提问，或询问其他问题。";
            return llmService.generateAnswerWithContext(question, relevantSegments, conversationContext)
                    + "\n\n\n该回复并未参考知识库内容，请注意甄别";
        }

        // 4. 调用 LLM 服务生成回答，加入对话历史作为上下文
        return llmService.generateAnswerWithContext(question, relevantSegments, conversationContext);
    }
}