明白了，我将基于你的要求，开始设计和实现一个完整、注释丰富、适合初学者理解的基于LLM的智能客服系统。

我会分模块开发，采用SpringBoot3后端、HTML/CSS/JavaScript前端，集成DJL本地模型和DeepSeek API远程模型支持，搭建简单粗暴的管理后台，满足小型企业的并发需求。开发中我会注重排版、结构清晰和讲解详细。

我会陆续把模块整理给你，一步步搭建，确保你能学懂每个环节。

# 基于LLM的智能客服系统设计与实现

在本方案中，我们将设计并实现一个完整的**智能客服系统**，充分利用大型语言模型（LLM）的能力来提升客服自动化水平。系统满足所提出的所有功能需求，包括意图识别、知识库检索、多轮对话、答案生成、用户管理、接口服务和人工转接等，同时满足相应的非功能性要求。下文将按照模块划分详细阐述设计与实现，并辅以代码示例（含丰富注释）和项目流程解释，使初学者也能清晰理解整个系统的开发流程。

## 系统功能概述

本智能客服系统主要包括以下功能模块：

- **意图识别**：基于LLM的意图分类模型，识别用户问题的类型或意图。
- **知识库管理**：支持通过文件上传方式构建知识库，利用文本嵌入向量和向量数据库实现高效内容检索。
- **多轮对话**：支持上下文管理，允许系统在多轮问答中保持对话连贯。
- **答案生成**：将检索到的知识内容与LLM结合，生成准确且丰富的回答。
- **用户管理**：提供后台接口管理客服系统用户（如新增、删除、修改用户信息）。
- **接口服务**：基于Spring Boot提供标准REST API供前端调用，实现前后端分离。
- **转人工客服**：根据规则（如置信度过低或用户明确要求）将会话转接给人工客服，并适当提示用户。

下面我们将对上述每一项功能要求进行详细设计，并给出相应的实现思路。

## 功能性需求详解

### 1. 意图识别

**功能说明**：意图识别模块负责分析用户输入的内容，从而判断用户的意图或问题类型。例如，区分用户是在询问产品信息、寻求技术支持、还是进行投诉等。通过意图分类，可以将后续处理流程路由到不同的子系统或采用不同的应答策略。

**实现思路**：利用大型语言模型强大的自然语言理解能力来完成意图分类。具体方案有两种：

- **基于预定义类别的LLM Prompt**：构造一个提示（prompt），让LLM基于给定的意图列表选择最符合用户问题的类别。例如，我们可以预先定义意图类别如「查询账户信息」「技术支持请求」「一般问答」「投诉建议」等，当用户输入问题时，将这些类别放入提示中，请LLM返回最可能的类别。由于LLM具备上下文理解和分类能力，这种方法无需训练专门的分类模型即可获得较高准确率。
- **微调或专用分类模型**：对于大型企业或特定领域，也可以训练一个专门的意图分类模型（例如基于BERT等的分类器）。不过对于初始版本，直接使用LLM通过提示进行意图判断更快捷。

**关键实现**：假设我们采用第一种方案，可以使用LLM API或本地模型以问答形式完成分类。伪代码示例如下：

```java
// 示例：使用LLM API进行意图识别的伪代码
String userMessage = "...";  // 用户的提问
String prompt = "请将用户意图分类为以下类别之一：\n"
    + "1. 查询账户信息\n"
    + "2. 技术支持请求\n"
    + "3. 一般问答\n"
    + "4. 投诉建议\n"
    + "用户问题：" + userMessage + "\n"
    + "请输出最符合的类别编号：";
    
String intentCategory = llmApi.getCompletion(prompt);
// llmApi.getCompletion 调用本地或远程LLM服务，获取模型输出的意图类别编号
```

在上述代码片段中，我们构造了一个包含候选意图类别的提示语交给LLM。模型返回的结果（如“2”或相应类别名称）即为预测的意图类别。接下来系统就可以依据该意图采取不同处理策略（例如检索不同知识库，或判断是否直接转人工等）。

**注意事项**：由于直接使用LLM进行意图分类可能受到提示词设计的影响，我们需要反复测试和优化提示语。此外，如果LLM返回的不确定或无关结果，需要增加**置信度判断**或**后处理规则**（例如当LLM输出置信度低或无法判断时，将意图标记为“未识别”，可以进一步采取默认回答或转人工处理）。

### 2. 知识库管理

**功能说明**：知识库管理模块允许管理员通过文件上传的方式导入公司内部知识。例如常见问题文档（FAQ）、产品说明书(txt格式)、Word文档(docx)或PDF文件等。系统应将知识库内容转换为便于检索的形式，以便在用户提问时快速找到相关信息片段供答案生成使用。

**实现思路**：采用**向量检索（Vector Search）**技术对知识库进行索引和查询。具体过程如下：

1. **文件解析与分段**：用户在后台上传知识库文件后，系统通过相应的解析库读取文本内容。例如：
   - txt文件可以直接按行或段读取；
   - docx文件可使用Apache POI库解析；
   - PDF文件可使用PDFBox等库提取文本。
   为了提高检索效果，一般将长文档按段落或固定大小的文本块进行拆分（如每隔N个句子或按照段落划分），以作为最小检索单元。
2. **文本嵌入（Embedding）生成**：将每个知识片段转换为向量表示。这一步需要一个预训练的文本嵌入模型。可以使用**SentenceTransformers**系列模型（如`all-MiniLM-L6-v2`）或其他适合中文的嵌入模型，通过**DJL**加载本地模型或调用外部API获取向量。 ([Building Chatbot using LLM based Retrieval Augmented Generation Method | by ritesh ratti, Ph.D | Medium](https://medium.com/@ritesh.ratti/building-chatbot-using-llm-based-retrieval-augmented-generation-method-4e854b65d925#:~:text=2,net)) ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=2,from%20multiple%20sources))得到的每个片段向量通常是高维浮点数向量（如384维或768维），可在向量空间表示该片段的语义。
3. **向量数据库存储**：将生成的向量以及对应的知识片段内容存储到向量数据库中，以支持后续相似度检索 ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=2.2.%20Embeds%20them%20using%20LLM,representations)) ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=match%20at%20L119%20The%20generated,based%20searches%20at%20scale))。常见的向量数据库包括**FAISS**（Facebook AI提供的高效相似检索库）、**Milvus**、**ElasticSearch (k-NN 插件)**等。对于中小型项目，也可以直接使用**Redis**的向量索引模块或将向量以blob形式存入MySQL，但专业的向量数据库在相似度查询性能上更具优势 ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=match%20at%20L119%20The%20generated,based%20searches%20at%20scale))。
4. **相似度检索**：当有用户提问时（详见答案生成模块），系统将用户的问题也转换为一个向量，并在向量数据库中执行相似度查询，找出**最相近的几个知识片段**。这些片段就是与用户问题语义相关的内容，可以用于增强LLM的回答。

**数据结构**：在数据库中，可以为知识库建立一张表存储文件的元数据（如文件名、上传时间、上传者等），以及一张表或索引存储**片段ID、文本内容和向量**。如果使用外部向量DB，则片段内容和其向量由该DB管理，我们只需保存引用。每个知识片段记录结构例如：
```
(id: 自增ID, doc_id: 所属文档ID, content: 文本内容, vector: 向量表示(blob或外部引用))
```

**示例代码**：下面给出一个简单的知识库构建流程示例（伪代码），包括文本分段和嵌入生成部分：

```java
// 假设我们有一个EmbeddingService，用于生成文本的向量表示
EmbeddingService embeddingService = new EmbeddingService(); 
VectorDatabase vectorDB = new MilvusVectorDB(); // 假设封装Milvus的操作类

public void addDocumentToKnowledgeBase(File file) {
    // 1. 解析文件获得文本
    String text = FileParser.parse(file);  // 根据文件类型，提取出纯文本字符串
    // 2. 将长文本按段落拆分为多个片段
    List<String> segments = TextUtils.splitTextToChunks(text, 200); 
    // 上述方法将文本每200字一段（可根据句子边界拆分，以保持语义完整）
    
    for (String segment : segments) {
        // 3. 对每个片段生成Embedding向量
        float[] vector = embeddingService.embedText(segment);
        // 4. 存储向量和文本到向量数据库
        vectorDB.insertVector(segment, vector);
    }
}
```

上述伪代码展示了知识库上传时的处理流程：读取文件 -> 拆分文本 -> 计算向量 -> 存储索引。实际实现中需要考虑文本清洗（去除特殊字符、空白）、以及避免插入重复片段等。

**技术要点**：使用DJL加载本地Embedding模型时，可以选择轻量的模型以提升速度；若使用远程Embedding服务（如OpenAI的embedding API或其他服务），需在后台配置API调用。无论哪种方式，向量检索技术RAG（Retrieval-Augmented Generation，即“检索增强型生成”）是本系统回答专业问题的核心：**通过在生成答案前检索相关知识，大幅提高答案的准确性和可靠性** ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=%E4%BD%BF%E7%94%A8RAG%E6%8A%80%E6%9C%AF%E5%90%8E%EF%BC%9A)) ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=RAG%EF%BC%8C%E5%8D%B3%E6%A3%80%E7%B4%A2%E5%A2%9E%E5%BC%BA%E7%94%9F%E6%88%90%EF%BC%88Retrieval))。

### 3. 多轮对话

**功能说明**：多轮对话模块确保用户和系统能够进行连续的对话，系统能够“记住”上下文。从用户体验看，就是用户在第n轮提问时，可以引用前面提到的信息而系统仍能理解。例如，用户先问“我想了解产品A的功能”，系统回答后，用户紧接着问“它的价格呢？”，系统需要知道“它”指的是前文的产品A。

**实现思路**：采用**上下文管理**策略来保存对话历史。在实现上可以通过以下手段：

- **会话ID**：每个对话分配唯一的会话ID（例如用户每打开一个客服聊天窗口，后台生成一个UUID会话标识）。在后端，建立数据结构将该会话ID与对应的对话历史关联存储。
- **对话存储**：对话历史可暂存于内存或缓存（如Redis）中，以支持高性能读写；同时也可异步存入数据库以备查。每一轮对话记录一般包括用户发言和系统回答两部分。可以用一个列表按时间顺序存储多轮问答对。
- **上下文截断**：由于LLM对输入长度有限制，不能无限制地将全对话历史都发送给模型。因此需要策略地**截断**或**摘要**。常用方法是在每次调用LLM生成回答时，只取最近若干轮对话作为上下文（比如3-5轮，或根据token长度裁剪）。对于更久远的上下文，可选择忽略或者通过总结形式纳入。
- **提示模板**：在调用LLM生成回答时，将选定的对话历史附在提示中，使模型参考。这可以采用类似对话格式：
  ```
  用户: 问题1
  客服: 回答1
  用户: 问题2
  客服: 回答2
  用户: 当前问题?
  客服: 
  ```
  让模型根据上文“客服:”之前的内容生成新的回答。通过在提示中包含历史对话，模型能够理解代词指代、上下文省略等，提高多轮对话的连贯性。

**数据结构**：可在后端维护如下结构：
```java
Map<String, List<Message>> conversationHistory = new HashMap<>();
```
其中key是会话ID，value是消息列表。`Message`可以是简单的类：
```java
class Message {
    String sender;  // "user" 或 "assistant"
    String content;
    LocalDateTime timestamp;
}
```
每次新的用户消息到来时，在对应会话的列表追加一条记录。当需要生成回复时，取该列表中最后若干条（包括最近的系统回复和用户提问）作为上下文。

**Redis的应用（可选）**：在高并发或需要持久化会话的场景下，可以将上述对话Map缓存到Redis中。Redis以内存存储，读写性能极高，非常适合存储会话状态数据。此外，如遇系统重启，Redis中的数据也可以用于会话恢复，增强健壮性。

### 4. 答案生成

**功能说明**：答案生成模块是系统的核心——**根据用户的问题和检索到的知识内容生成精准答案**。它需要综合用户提问、知识库相关信息以及上下文，对答案进行自然语言生成。这部分主要利用LLM的强大生成能力，同时结合知识库内容以确保答案可靠。

**实现思路**：采用**检索增强生成（RAG）**模式来生成答案 ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=RAG%EF%BC%8C%E5%8D%B3%E6%A3%80%E7%B4%A2%E5%A2%9E%E5%BC%BA%E7%94%9F%E6%88%90%EF%BC%88Retrieval))。当用户提问到来时，系统按照以下流程生成回答：

1. **知识检索**：利用“知识库管理”模块中提到的向量检索，找到与**当前问题**最相关的若干知识片段（例如得分最高的前3个片段）。这些片段可能包含直接回答问题的信息点。
2. **构建提示（Prompt）**：将**检索到的知识片段**和**当前对话上下文**结合，构造成一个发送给LLM的提示。例如：
   ```
   [知识库片段1]\n
   [知识库片段2]\n
   [知识库片段3]\n
   用户问：{用户问题}\n
   请结合以上资料回答用户的问题。
   ```
   这样LLM在生成回答时，会参考提供的知识内容，避免“无中生有”或回答不准确。 ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=))
3. **LLM生成**：调用LLM（本地模型或远程API）对上述提示执行**补全/对话**任务，得到答案文本。由于提示中加入了外部知识，LLM的生成相当于受控于权威资料，从而提高准确性并减少幻觉错误 ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=%E4%BD%BF%E7%94%A8RAG%E6%8A%80%E6%9C%AF%E5%90%8E%EF%BC%9A))。
4. **答案优化处理**：对模型生成的原始回答可以进行适当的后处理，例如：
   - 如果答案过长，考虑摘要关键要点；
   - 如果答案缺少格式，可以加上换行或列表使其更清晰；
   - 监测敏感信息，确保不泄露内部机密（这可结合LLM的内容过滤或在提示中加入限制）。
5. **记录对话**：将问题和答案追加到该会话的对话历史中，以用于后续多轮交互。

**代码示例**：以下展示答案生成主要流程的伪代码实现：

```java
public String generateAnswer(String sessionId, String userQuestion) {
    // 1. 知识库检索：将用户问题转为向量，在向量DB中查询相似片段
    float[] questionVector = embeddingService.embedText(userQuestion);
    List<String> relatedDocs = vectorDB.searchSimilar(questionVector, 3);
    // relatedDocs 返回最相近的3个知识片段文本
    
    // 2. 获取上下文：从会话历史获取最近的几轮对话
    List<Message> history = conversationHistory.get(sessionId);
    String contextPrompt = buildContextPrompt(history); 
    // 将history格式化为prompt字符串，例如"用户: ...\n客服: ...\n用户: ...\n客服: ...\n用户: 当前问题\n客服:"
    
    // 3. 构建提示，包含知识片段和上下文
    String knowledgePrompt = String.join("\n", relatedDocs);
    String finalPrompt = knowledgePrompt + "\n" 
                       + "基于以上信息，回答用户问题：\n" 
                       + contextPrompt;
                       
    // 4. 调用LLM生成答案
    String rawAnswer = llmService.generate(finalPrompt);
    
    // 5. 简单后处理（例如去除不必要的客套、截断超长回答等）
    String answer = postProcessAnswer(rawAnswer);
    
    // 6. 将本轮 Q&A 存入历史记录
    history.add(new Message("user", userQuestion, LocalDateTime.now()));
    history.add(new Message("assistant", answer, LocalDateTime.now()));
    
    return answer;
}
```

在上述代码中：
- `vectorDB.searchSimilar` 方法利用预先建立的向量索引，找出语义上最相关的知识片段。
- `buildContextPrompt` 函数负责把多轮对话历史转换为提示的一部分（注意控制长度）。
- `llmService.generate` 封装了对LLM模型的调用（无论是本地的DJL模型还是远程API）。
- 返回前将答案存入会话历史以备下文。

**LLM服务的集成**：这里`llmService`可能有两个实现模式（在后台管理界面可配置切换）：
  - **本地模式**：使用DJL加载本地的大语言模型。如使用HuggingFace上的开源模型（比如一个中文对话模型）进行推理。DJL提供了对Transformers模型的支持，可以通过模型名称或路径直接加载 ([HuggingFace Accelerate User Guide - Deep Java Library](https://docs.djl.ai/master/docs/serving/serving/docs/lmi/user_guides/hf_accelerate.html#:~:text=LMI%27s%20HuggingFace%20Accelerate%20supports%20most,LMI%2C%20and%20corresponding%20model))。例如，我们可以使用DJL加载一个ChatGLM或Llama2模型，使用`model.newPredictor()`进行推理。**注意**：LLM模型通常非常大，部署本地需要充足的内存和GPU支持，否则可能需要用小型模型或蒸馏版。
  - **远程API模式**：调用第三方的LLM服务接口，如题目中提到的DeepSeek。这类服务通常提供OpenAI兼容的REST API ([DeepSeek API Docs: Your First API Call](https://api-docs.deepseek.com/#:~:text=The%20DeepSeek%20API%20uses%20an,compatible%20with%20the%20OpenAI))。我们可以使用HTTP请求将prompt发送到远程并获取回答。同样也可以使用OpenAI的官方SDK，通过设置API的endpoint为DeepSeek的地址来调用（因为DeepSeek宣称兼容OpenAI API格式）。

**DeepSeek API 调用示例**：如果选择远程模式，我们可以通过Spring的RestTemplate或HttpClient调用DeepSeek。例如（伪代码）：

```java
// 使用RestTemplate调用DeepSeek (OpenAI兼容格式)
RestTemplate rest = new RestTemplate();
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.setBearerAuth(DEEPSEEK_API_KEY);  // 鉴权

JSONObject reqBody = new JSONObject();
reqBody.put("model", "deepseek-chat-v3"); 
reqBody.put("messages", Arrays.asList(
    Map.of("role","user", "content", finalPrompt)
));

HttpEntity<String> request = new HttpEntity<>(reqBody.toString(), headers);
String apiUrl = "https://api.deepseek.com/v1/chat/completions"; // 假设的DeepSeek聊天补全接口
ResponseEntity<String> response = rest.postForEntity(apiUrl, request, String.class);
String answer = parseAnswerFromJSON(response.getBody());
```

上面构造的请求遵循OpenAI ChatCompletion API格式 ([DeepSeek API Docs: Your First API Call](https://api-docs.deepseek.com/#:~:text=The%20DeepSeek%20API%20uses%20an,compatible%20with%20the%20OpenAI))，DeepSeek如果完全兼容则可以直接这样调用。`parseAnswerFromJSON`需实现从返回结果中提取出模型回答文本。

**小结**：答案生成模块通过“知识检索 + 上下文 + LLM”相结合，实现了专业且上下文相关的回答生成。这种RAG方案能够**提高回答准确性**、**保持信息实时性**并**降低LLM出现幻觉的风险** ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=%E4%BD%BF%E7%94%A8RAG%E6%8A%80%E6%9C%AF%E5%90%8E%EF%BC%9A))。

### 5. 用户管理

**功能说明**：用户管理模块提供后台界面或接口，用于管理客服系统的用户账号信息。这通常包括增删改查用户、分配角色权限等。对于我们的项目，假定需要一个简单的用户体系（例如客服人员账号、管理员账号等），以控制对后台功能的访问。

**实现思路**：基于Spring Boot和MySQL，实现常规的**CRUD**接口即可。主要工作包括：

- **数据库设计**：建立用户表（如`users`），字段包括用户ID、用户名、密码（加密存储）、角色/权限、联系方式等基础信息。密码等敏感字段需使用加密哈希存储，如使用Spring Security提供的PasswordEncoder对密码进行单向散列 ([Password Storage :: Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#:~:text=Spring%20Security%27s%20PasswordEncoder%20interface%20is,the%20password%20be%20stored%20securely))。
- **后台接口**：使用Spring MVC的REST控制器提供用户增删改查的API。例如：
  - `POST /admin/users` 创建新用户
  - `GET /admin/users` 或 `/admin/users/{id}` 查询用户
  - `PUT /admin/users/{id}` 修改用户信息
  - `DELETE /admin/users/{id}` 删除用户
- **参数校验**：对输入的数据进行校验避免不良数据。如用户名重复检查、密码强度校验等。
- **权限控制**：这些接口应该受限于管理员角色才能调用，防止普通用户越权管理账号。可利用Spring Security配置基于角色的访问控制（详见“安全性”部分）。

**实现示例**：下面以创建用户为例给出简化的代码片段，重点展示**注释**和逻辑：

```java
@RestController
@RequestMapping("/admin")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    // 创建新用户
    @PostMapping("/users")
    public ResponseEntity<String> createUser(@RequestBody User newUser) {
        // 对密码进行哈希加密后再存储
        String rawPassword = newUser.getPassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);  // Spring Security提供的PasswordEncoder
        newUser.setPassword(encodedPassword);
        
        userService.save(newUser);
        return ResponseEntity.ok("用户创建成功");
    }
    
    // 获取用户信息
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if(user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
    
    // ... 其他更新、删除接口类似
}
```

上述`UserController`通过`@RestController`定义REST接口，并使用`@RequestMapping("/admin")`归组。`passwordEncoder`是Spring Security常用的接口，用于安全地存储密码 ([Password Storage :: Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#:~:text=Spring%20Security%27s%20PasswordEncoder%20interface%20is,the%20password%20be%20stored%20securely))。`UserService`封装了对`User`实体的数据库操作（典型地使用Spring Data JPA或MyBatis实现）。每个方法都包含必要的注释，易于理解。

### 6. 接口服务（前后端交互）

**功能说明**：系统采用前后端分离架构，通过标准接口提供服务。前端（HTML/JS）将通过HTTP请求与后端交互。接口服务模块即定义和实现这些HTTP API，包括聊天对话接口和后台管理接口等。

**设计原则**：使用**RESTful API**风格设计接口，清晰定义URL路径和HTTP动词的语义。例如：
- `POST /api/chat` ：前端提交用户问题，获取AI回答（开启新对话或单轮问答）。
- `POST /api/chat/{sessionId}`：基于已有会话ID继续聊天（多轮对话续接）。
- `POST /api/upload`：上传知识库文件，后台处理并更新知识库。
- `/admin/users` 等：按上节用户管理定义的路径。

所有接口通过Spring Boot的`@RestController`实现，返回JSON格式数据，方便前端解析显示。下面重点说明**聊天接口**的实现：

**聊天接口实现**：假设前端每次将用户输入发送到`/api/chat`，后台需要做：
  1. 如果是新会话，没有sessionId，则创建一个新的会话ID，并初始化会话历史。
  2. 调用前述意图识别模块判断意图类型。
  3. 调用答案生成模块生成回答内容。
  4. 将回答连同会话ID一起返回前端。

代码示例：

```java
@RestController
@RequestMapping("/api")
public class ChatController {
    
    @Autowired
    private ChatService chatService;  // ChatService封装聊天逻辑
    
    // 提交问题并获取回答
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        String question = request.getQuestion();
        
        // 如果没有传入会话ID，则创建新的会话
        if(sessionId == null || sessionId.isEmpty()) {
            sessionId = chatService.startNewSession();
        }
        // 调用聊天服务获取回答
        String answer = chatService.handleUserQuestion(sessionId, question);
        
        // 封装响应，包括会话ID和答案
        ChatResponse resp = new ChatResponse(sessionId, answer);
        return ResponseEntity.ok(resp);
    }
}
```

这里定义了`ChatRequest`和`ChatResponse`两个简单的DTO类用于请求/响应格式：
```java
class ChatRequest {
    private String sessionId;
    private String question;
    // getters and setters ...
}
class ChatResponse {
    private String sessionId;
    private String answer;
    // constructor, getters ...
}
```

`chatService.handleUserQuestion(sessionId, question)`内部实现实际上执行：意图识别 -> 知识检索 -> 答案生成 -> （可能的人工转接判断，见下文）等步骤，并返回最终答案字符串。

前端拿到`ChatResponse`后，可以显示`answer`给用户。如果是新会话，还需要保存`sessionId`以在用户继续提问时附带该ID，实现多轮会话。

**文件上传接口**：知识库文件上传通常使用`@PostMapping`并接收`MultipartFile`参数。Spring Boot可很方便地处理文件上传。上传后调用前述知识库管理模块的方法进行解析和入库。接口设计例如：
```java
@PostMapping(value="/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<String> uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
    try {
        knowledgeService.importFile(file);
        return ResponseEntity.ok("知识库更新成功");
    } catch(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件处理失败");
    }
}
```
这里`knowledgeService.importFile`内部会保存文件（如到本地磁盘或OSS）以及调用向量生成逻辑。

### 7. 转人工客服

**功能说明**：当AI无法很好地回答用户问题时，系统需支持将对话转接给真人客服。这可以通过两种情况触发：
- **低置信度**：如果答案生成模块对于用户问题的信心不足（可能根据意图识别结果或知识检索结果判断）。
- **用户请求**：用户明确要求 "转人工"、"人工客服" 等关键词时。

**实现思路**：需要设计一个**规则引擎或条件判断**，在生成答案前或后检查是否满足转人工条件：

- **置信度判断**：可以通过检索阶段的结果来简单评估。如果向量数据库没有检索到高相关度的片段（例如最高相关度低于某阈值），说明知识库可能无相应答案，AI回答可靠性降低，此时倾向于转人工。或者在LLM调用返回的结果中检测模型的不确定回答（例如包含“抱歉，我不确定”之类的措辞），也可作为低信心的标志。
- **用户请求识别**：这其实也是意图识别的一种，可在意图分类中增加一个类别如“要求人工帮助”。当LLM判定用户意图是要求人工时，直接触发转人工。

**转接流程**：一旦决定转人工，系统可以采取以下动作：
1. **通知用户**：系统回复一条消息告知用户“已为您转接人工客服，请稍候”。这可以是固定回复模板。
2. **结束AI对话**：标记该会话状态为需要人工介入，后续不再由AI回答。可以将此状态存储在会话记录中或通知后台坐席系统。
3. **通知人工坐席**：如果有对接工单或客服坐席系统，可通过API或消息队列通知人工坐席有用户在等待服务。如果没有复杂系统，至少在管理后台界面上将该会话标记为“人工介入中”，客服人员可以手动接手。

**实现示例**：在`ChatService.handleUserQuestion`内部伪代码体现这个逻辑：

```java
public String handleUserQuestion(String sessionId, String question) {
    // 1. 意图识别
    String intent = intentService.classify(question);
    if(intent.equals("人工请求")) {
        // 用户明确要求人工
        markSessionAsHuman(sessionId);
        return "好的，正在为您转接人工客服，请稍候…"; // 固定提示语
    }
    // 2. 知识检索
    List<String> docs = vectorDB.searchSimilar(embeddingService.embedText(question), 3);
    // 检查最高相关度，如果低于阈值
    if(vectorDB.getTopScore() < 0.5) {  
        markSessionAsHuman(sessionId);
        return "抱歉，我暂时无法回答您的问题，已为您转接人工客服。";
    }
    // 3. 正常利用LLM生成答案
    String answer = generateAnswer(sessionId, question, docs);
    return answer;
}
```

上述伪代码中，`markSessionAsHuman`函数将该会话的状态保存（可以在conversationHistory里附加一个标志位，或维护一个单独的集合记录人工介入的会话）。`vectorDB.getTopScore()`表示最近一次`searchSimilar`查询中最相关片段的相似度得分，用于粗略判断知识匹配程度。如果没有高匹配的知识，系统选择不冒险给出可能错误的回答，而是交由人工处理。

此外，前端在收到“转人工”回复时，也可以提示用户接入人工。在更高级的实现中，前端可以切换到人工聊天模式（例如打开人工座席的Web聊天窗口等），但这些属于扩展功能，超出本设计范围。

## 非功能性需求落实

除了功能开发，本系统还需要满足一些非功能性要求，包括开发环境、性能、安全、部署模式等方面。下面针对题目列出的每一点进行说明：

- **开发环境：IDEA** – 系统使用Java语言开发，推荐使用IDEA作为IDE进行开发和调试。开发者可通过Maven管理依赖，使用IDEA的Maven集成方便地构建和运行Spring Boot项目。虽然IDE并不影响最终系统功能，但IDEA对Java初学者较为友好，可以利用其图形化调试、自动补全和插件提高开发效率。
- **后端框架：Spring Boot 3** – 采用Spring Boot 3.x作为后端框架。这一版本基于Spring 6和Java 17，提供了最新的功能和性能改进。Spring Boot高度集成了Web开发所需组件（如Spring MVC、Spring Data、Spring Security等），能快速构建独立运行的后端应用。通过在`pom.xml`中引入spring-boot-starter-web等依赖，我们可以轻松启动一个内嵌Tomcat的HTTP服务，并采用注解的方式定义控制器和业务层Bean，符合本项目快速开发的需求。
- **前端框架：HTML/CSS/JavaScript** – 前端部分不使用复杂的框架，考虑到重点在后台智能客服逻辑，实现一个**简洁的Web界面**即可。这可以通过一个静态的HTML页面加少量JavaScript完成。例如：
  - 聊天窗口：使用HTML的`<textarea>`或`<div>`显示对话内容，下面有一个输入框和发送按钮。
  - 当用户输入问题后，JavaScript使用Fetch或XHR调用后台`/api/chat`接口，将问题发送并获取回答，再将回答追加到聊天窗口显示。
  - 简单的CSS用于美化对话气泡样式，使用户体验更友好。
  后续可以逐步引入前端框架（如Vue/React）进行丰富，但初版强调**简单易实现**。
- **数据库：MySQL** – 选用MySQL来存储系统的结构化数据，例如**用户信息、知识库元数据、对话日志**等。MySQL以其成熟稳定和易用性著称，小型企业应用完全可以胜任。通过Spring Data JPA或MyBatis框架，我们可以方便地进行数据库CRUD操作。需要注意配置连接池以保证在100并发左右时的性能。数据表设计方面：
  - 用户表(users)：字段如id, username, password(hash), role, created_time等。
  - 知识库文件表(docs)：字段如doc_id, file_name, uploader, upload_time等。
  - （如果需要）对话记录表(chats)：字段如 chat_id, session_id, user_question, ai_answer, timestamp 等，用于保存聊天日志。
  实际实现中，也可以仅将聊天记录存在内存/Redis，并选择性地持久化部分有价值的数据。
- **缓存：Redis（可选）** – 引入Redis主要是为了**提升知识检索速度和会话管理**性能：
  - **知识检索加速**：如果向量数据库查询较慢，或我们直接在内存中计算向量相似度，那么将常用的知识片段缓存到Redis可减少磁盘或远程查询次数。比如可以缓存最近N次查询结果、热门问题的答案等。
  - **会话状态管理**：利用Redis的内存存储，将`sessionId -> conversationHistory`映射存入Redis，可在分布式部署时实现各节点共享会话状态，避免粘滞会话问题。Redis天然支持基于key过期，这样对于长时间不活动的会话可以自动淘汰释放内存。
  Redis是可选组件。如果初始并发和数据量不大时，可以暂不引入，后续随着用户增多再行优化。
- **构建工具：Maven** – 采用Maven进行项目构建与依赖管理。Maven是Java领域主流的构建工具，它通过`pom.xml`配置项目依赖坐标，会自动下载所需jar包（如Spring Boot、DJL、MySQL驱动等）。使用Maven可以方便地打包部署（`mvn package`生成可执行的jar），并与CI/CD流程集成。对于初学者，也便于在IDE中直接导入Maven项目，减轻配置负担。
- **性能要求：100并发** – 为了支撑小型企业的使用，需要考虑一定的并发性能。100并发的设计目标对现代Web后端来说不算很高，但考虑到LLM调用的开销，要**特别优化AI相关处理**：
  - 对于LLM本地部署，要确保模型推理速度：可以选择相对小的模型或启用GPU支持。如果使用GPU服务器部署，本地模型在并发情况下可以开多个线程处理请求，但要防止GPU显存不足。
  - 使用异步请求：Spring Boot可以结合CompletableFuture或WebFlux进行异步处理，在调用外部API（如DeepSeek）时不会阻塞主线程，从而提升吞吐。
  - 缓存和重用向量：对于重复问题或高频知识，可以缓存答案或向量，避免每次都从头计算。
  - 资源池配置：调整数据库连接池（如HikariCP）的大小，合理配置线程池，确保100个并发请求下系统资源不耗尽。前期压测模拟100并发场景，观察CPU、内存、响应时间进行优化。
- **安全性：Spring Security鉴权** – 系统需要基础的安全防护，特别是管理接口应当受到保护避免任意调用。通过集成Spring Security，我们可以实现：
  - **接口鉴权**：如前述用户管理和文件上传接口仅管理员角色可访问；聊天接口可以对登录用户或游客开放，根据需要决定。Spring Security可基于注解如`@PreAuthorize("hasRole('ADMIN')")`来限制方法访问。
  - **登录认证**：实现一个简单的登录流程。可以使用Spring Security的表单登录机制或JWT（JSON Web Token）无状态认证。由于前后端分离，倾向于JWT：用户登录成功后前端保存Token，每次请求时附在请求头（Authorization: Bearer ...），后端验证Token有效性。 ([DeepSeek API Docs: Your First API Call](https://api-docs.deepseek.com/#:~:text=The%20DeepSeek%20API%20uses%20an,compatible%20with%20the%20OpenAI))
  - **数据加密存储**：除密码采用哈希外，系统中如果涉及敏感信息（例如DeepSeek的API密钥、用户隐私数据），应使用加密手段保存。如在配置文件中通过属性加密存储，数据库字段使用JPA AttributeConverter结合JCE加密算法保存等。这样即使数据库泄露，敏感数据也无法直接被读取。
  - **其它安全措施**：防止常见Web漏洞，如在接口上对用户输入进行校验过滤（防SQL注入、XSS攻击等），开启Spring Boot自带的CSRF防护（视情况，若使用JWT可以关掉CSRF）。对于文件上传接口，校验文件类型和大小，避免恶意文件上传导致安全问题。
- **LLM集成模式切换** – 系统提供**本地部署**和**远程API**两种LLM调用模式，并允许在管理后台配置切换。这可以通过以下实现：
  - **配置开关**：在应用配置中增加一个标志，如`llm.mode = local`或`api`。或者更灵活地，在数据库的配置表里存一个当前模式值，由后台管理界面提供修改选项。读取该值决定LLM调用走哪个实现。
  - **策略模式封装**：创建一个接口`LLMService`，定义统一的方法如`generate(prompt)`用于生成回答。实现两个类`LocalLLMService`和`RemoteLLMService`分别处理本地模型调用和API调用。可以使用Spring的`@ConditionalOnProperty`注解，根据配置动态装配对应的Bean，也可以在运行时检查模式值每次选择调用哪个服务。
  - **本地部署(DJL+HuggingFace)**：如前所述，配置DJL加载特定的模型。例如使用HuggingFace的模型仓库路径或名称：`Criteria.builder().optApplication(Application.NLP.TEXT_GENERATION).optEngine("PyTorch").optOption("modelUrls","路径或模型名称")...` 等配置加载模型 ([HuggingFace Accelerate User Guide - Deep Java Library](https://docs.djl.ai/master/docs/serving/serving/docs/lmi/user_guides/hf_accelerate.html#:~:text=LMI%27s%20HuggingFace%20Accelerate%20supports%20most,LMI%2C%20and%20corresponding%20model))。需要准备好模型文件（可能较大）放在指定位置供DJL读取。注意内存和加载时间，可以在应用启动时提前加载模型以避免首次调用的延迟。
  - **远程API(DeepSeek)**：需要在配置中保存API访问所需的信息（URL、密钥等）。实现RemoteLLMService时使用HttpClient调用。要注意网络超时控制，调用失败的处理（如重试或返回错误提示）。
  在管理后台提供一个切换按钮，当管理员选择不同模式时，更新配置并**重启/通知后台服务**使用新的模式（某些情况下可以做到不停机切换，如设计LLMService为热插拔的，但初步实现可以简单粗暴要求切换后重启服务加载不同配置）。

## 系统架构与模块设计

**总体架构**：本智能客服系统采用典型的分层架构，结合AI推理流程，整体结构如下：

- **表示层（前端）**：一个Web静态页面提供聊天界面，以及简单的管理界面（可登录的后台页面，用于上传文件、管理用户等）。通过HTTP API与后端通信。
- **控制层（Controller）**：后端Spring Boot定义若干REST Controller，包括`ChatController`, `UserController`, `KnowledgeController`等，分别处理聊天交互、用户管理、知识库管理等请求。控制层主要进行参数解析、权限验证，然后调用业务服务层完成具体逻辑。
- **业务逻辑层（Service）**：封装核心功能逻辑，每个功能模块对应一个服务类：
  - `IntentService`：意图识别服务
  - `KnowledgeService`：知识库管理服务
  - `ChatService`：聊天对话服务（包括多轮对话管理、调用LLM生成答案等）
  - `UserService`：用户管理服务（对接UserRepository进行数据库操作）
  - `LLMService`：LLM调用服务（可以有本地/远程两种实现）
  - 等等。
- **数据访问层（Repository/DAO）**：通过Spring Data JPA定义Repository接口，或使用MyBatis的Mapper，实现对MySQL的CRUD。典型的有UserRepository, DocumentRepository等。
- **外部组件**：LLM模型（本地部署）或 LLM API、向量数据库（如Milvus）、缓存（Redis）等作为独立组件与服务层交互。
- **安全框架**：Spring Security配置类，会在过滤器链中对请求进行认证与鉴权。使用JWT时，有JWT过滤器验证Token后将用户身份注入SecurityContext。

以下是系统各模块和流程的工作示意：

 ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=2,from%20multiple%20sources)) ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=In%20this%20step%20we%20feed,data%20to%20the%20vector%20database))

- 用户通过前端发送问题 -> ChatController 接收请求，创建会话/加入历史 -> 调用 ChatService。
- ChatService 调用 IntentService 进行意图识别。如果需要转人工则短路返回。
- ChatService 调用 KnowledgeService 或直接使用向量DB接口进行检索，获取相关知识片段。
- ChatService 调用 LLMService（根据配置选择Local或Remote实现）生成回答文本。
- ChatController 将回答返回前端，前端显示给用户。
- 管理员通过前端管理页面可调用 KnowledgeController 上传文件、UserController 管理用户，这些请求经Security校验后由各自Service处理，对数据库或向量DB进行相应更新。

**模块划分与代码组织**：在实际项目中，代码可以按照功能归类放置，例如：

```
com.example.chatbot
├── controller
│   ├── ChatController.java
│   ├── UserController.java
│   └── KnowledgeController.java
├── service
│   ├── ChatService.java
│   ├── IntentService.java
│   ├── KnowledgeService.java
│   ├── LLMService.java        (接口)
│   ├── LocalLLMService.java   (实现)
│   ├── RemoteLLMService.java  (实现)
│   └── UserService.java
├── model
│   ├── User.java
│   ├── Document.java
│   ├── Message.java
│   └── ... (其他实体或DTO)
├── repository
│   ├── UserRepository.java
│   └── DocumentRepository.java
├── config
│   ├── SecurityConfig.java    (Spring Security配置)
│   ├── LLMConfig.java         (LLM模式、自定义Bean配置)
│   └── RedisConfig.java       (如果使用Redis的话)
└── util
    ├── FileParser.java
    ├── TextUtils.java
    └── ...
```

每个文件和类都应包含详尽的注释，解释其作用和重要逻辑，帮助零基础的新人理解代码。

## 项目流程与运行说明

最后，我们以一个用户实际提问获取答案的流程，以及管理员维护系统的流程，来说明系统如何运行，各部分如何协同：

### 用户提问会话流程

1. **用户访问界面**：用户打开客服网页，浏览器加载聊天页面（可能需要用户先登录，视业务而定）。页面通过JavaScript建立与后台的通信（可以是直接Ajax轮询，也可以用WebSocket实现即时性，但本方案使用简单轮询即可）。
2. **用户发送问题**：用户在输入框键入问题，如“请问你们产品的保修期多久？”，点击发送。前端将此内容通过`POST /api/chat`请求发给后端。由于是首次提问，没有sessionId，前端可以不传该字段或传空。
3. **后端创建会话**：ChatController收到请求，检测到没有sessionId，于是调用`chatService.startNewSession()`创建一个新的会话ID（例如生成UUID `"abc123"`）并初始化会话历史（空列表）。然后调用`chatService.handleUserQuestion("abc123", "请问你们产品的保修期多久？")`处理问题。
4. **意图识别**：`IntentService.classify(...)`判断用户意图，可能识别为“产品咨询”类别。由于不是要求人工且意图明确，继续。
5. **知识检索**：ChatService将问题通过Embedding模型转为向量，在向量数据库中查找匹配片段。假设在知识库中找到了三段相关内容，例如：
   - 知识片段1：“我们产品默认保修期为一年。”
   - 知识片段2：“保修需保留购买凭证……”
   - 知识片段3：“延保服务可以购买延长至两年。”
6. **LLM生成回答**：ChatService构造prompt，包括上述知识片段和用户提问，并调用LLMService生成回答。假如配置的是远程模式，则RemoteLLMService将请求发送给DeepSeek API。DeepSeek的大模型根据prompt返回了一个回答，例如：“您好，我们的产品提供**一年的免费保修**服务。如果您需要更长时间的保障，我们也提供购买延长保修至两年的选项。在保修期内，出现非人为损坏的问题都可以免费维修或更换。【注意保存购买凭证】哦！”
7. **返回结果**：ChatService拿到LLM返回的回答文本，做了简单处理（比如保证格式友好），然后返回给ChatController。ChatController包装成ChatResponse JSON，包括会话ID "abc123" 和回答内容，HTTP响应给前端。
8. **前端显示**：浏览器收到响应，通过JavaScript将回答插入聊天窗口。同时保存会话ID "abc123"（可以存在cookie或全局变量中），以便后续提问继续使用该ID。用户看到客服机器人的回复，并可以继续提问。
9. **多轮追问**：如果用户接着问：“延长保修怎么购买？”，前端这次发送请求时会附上sessionId "abc123"。后端收到后在ChatService中会取出历史对话，将“请问你们产品的保修期多久？”->“回答…”作为上下文，加上新问题，通过相似流程再次生成答案。如有需要也再次检索知识库（比如找延保购买相关的内容）。这样实现了对话的连贯。
10. **转人工示例**：倘若用户问了一个知识库没有的问题，例如“你们产品明年会出新款吗？”，向量检索可能找不到相关内容或相关度很低。ChatService检测到低于阈值，便返回转人工提示。系统回复用户“抱歉，我无法回答，正在转接人工”，并将此会话标记需要人工跟进。管理员在后台看到提醒，可以手动介入对该用户的对话进行回复。

### 管理员维护流程

1. **登录**：管理员通过后台登录页面（简单的表单提交到`/login`接口，Spring Security处理）登录获取JWT或Session，随后访问受保护的管理接口。
2. **上传知识库**：管理员在后台界面选择新的常见问题文档上传。前端将文件通过`/api/upload`接口发送，后端KnowledgeController调用KnowledgeService处理文件。解析文本->生成向量->存储到向量数据库。上传成功后页面提示“知识库更新成功”。从此新的提问可以命中这些更新的知识。
3. **用户管理**：管理员访问用户管理界面，可以填写表单新增用户（比如新增一个客服人员账号），表单提交到`POST /admin/users`。UserController验证权限后调用UserService保存用户，密码使用BCrypt加密 ([Password Storage :: Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#:~:text=Spring%20Security%27s%20PasswordEncoder%20interface%20is,the%20password%20be%20stored%20securely))后存入MySQL。管理员也可以通过界面发送请求删除或修改用户信息，对应调用不同接口。
4. **LLM模式切换**：管理员在系统设置页选择LLM模式为“本地”或“远程”。假设这一选择通过`POST /admin/settings`接口提交，后端收到后更新配置（比如修改数据库中的配置值或触发某个Bean刷新）。如果实现了动态切换，则ChatService或LLMService会即时切换策略；否则管理员在更改后需要重启服务使配置生效。切换到本地模式后，系统将使用部署在服务器本地的模型来回答问题，这对数据敏感的企业有意义（数据不出内网）；切换到远程模式则利用云端强大的模型，以获取更高质量回答。

## 总结

以上即为基于大语言模型的智能客服系统的完整设计与实现说明。从需求分析、技术选型到模块划分、代码示例以及运行流程，我们进行了深入的讲解。通过本方案，小型企业可以搭建起一个功能完善的智能客服系统：

- **对用户**：提供7x24小时自动应答，涵盖常见问题解答并能进行多轮深入交流，在多数情况下解决用户问题，提高满意度和效率。
- **对企业**：减轻人工客服压力，仅在复杂问题时再介入，节省人力成本。同时系统具备良好的可扩展性和安全保障，可逐步优化并接入更强大的模型。

本项目也适合作为学习大型项目开发的范例。初学者可以从中体会**分层架构设计**、**模块解耦**、**接口规范**、**数据库设计**以及**AI工程集成**等方面的实践要点。在实现过程中，建议先搭建基础的问答流程，再逐步添加各项功能，确保每个模块都理解透彻再整合。通过不断的调试和优化，相信最终的系统能够稳定运行并投入实际使用，为用户带来良好体验。

**参考文献：**

- 大型语言模型在客服系统中的应用设计 ([如何利用大型语言模型（LLM）打造高效智能客服系统？_的设计_需求_用户](https://m.sohu.com/a/823504364_122004016/?pvid=000115_3w_a#:~:text=,%E8%BD%AC%E6%8E%A5%E6%9C%BA%E5%88%B6%EF%BC%9A%E4%BF%9D%E8%AF%81%E9%81%87%E5%88%B0%E5%A4%8D%E6%9D%82%E9%97%AE%E9%A2%98%E6%97%B6%E8%83%BD%E5%8F%8A%E6%97%B6%E8%BD%AC%E7%BB%99%E4%BA%BA%E5%B7%A5%E5%AE%A2%E6%9C%8D%E3%80%82%20%E5%90%88%E7%90%86%E7%9A%84%E6%B5%81%E7%A8%8B%E8%AE%BE%E8%AE%A1%E5%B0%86%E6%8F%90%E5%8D%87%E7%B3%BB%E7%BB%9F%E7%9A%84%E7%A8%B3%E5%AE%9A%E6%80%A7%E5%8F%8A%E7%94%A8%E6%88%B7%E4%BD%93%E9%AA%8C%E3%80%82)) ([AI大模型时代，如何用RAG技术重塑传统智能客服问答机器人？_对话_各行各业_企业](https://www.sohu.com/a/766904140_114819#:~:text=RAG%EF%BC%8C%E5%8D%B3%E6%A3%80%E7%B4%A2%E5%A2%9E%E5%BC%BA%E7%94%9F%E6%88%90%EF%BC%88Retrieval)) 
- 向量数据库支持的聊天机器人架构 ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=2,from%20multiple%20sources)) ([Designing the System Architecture for a Vector DB-Powered Chatbot | by Chetan londhe | Apr, 2025 | Medium](https://medium.com/@chetanlondhe1112/designing-the-system-architecture-for-a-vector-db-powered-chatbot-edc345db7ddc#:~:text=match%20at%20L119%20The%20generated,based%20searches%20at%20scale)) 
- Spring Security密码安全存储实践 ([Password Storage :: Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#:~:text=Spring%20Security%27s%20PasswordEncoder%20interface%20is,the%20password%20be%20stored%20securely))

