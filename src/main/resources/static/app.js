// 当整个HTML文档加载完成后，执行这个函数
document.addEventListener('DOMContentLoaded', function() {
    // 获取页面上的HTML元素，以便后续操作它们
    // getElementById是用来根据HTML元素的id属性找到对应元素的方法
    const chatContainer = document.getElementById('chatContainer'); // 聊天内容显示区域
    const userInput = document.getElementById('userInput');         // 用户输入框
    const sendBtn = document.getElementById('sendBtn');             // 发送按钮
    const uploadForm = document.getElementById('uploadForm');       // 上传文件的表单
    const fileInput = document.getElementById('fileInput');         // 文件选择输入框
    const uploadBtn = document.getElementById('uploadBtn');         // 上传按钮
    const uploadStatus = document.getElementById('uploadStatus');   // 上传状态显示区域
    const fileList = document.getElementById('fileList');           // 文件列表显示区域

    // 页面加载时，调用函数加载已上传的知识库文件列表
    loadFileList();

    // 不再强制检查登录状态
    // checkUserLoginStatus();

    // 为发送按钮添加点击事件监听器，当用户点击时调用sendMessage函数
    sendBtn.addEventListener('click', sendMessage);
    
    // 为输入框添加键盘事件监听器，当用户按下Enter键且没有同时按Shift键时发送消息
    userInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault(); // 阻止默认行为（换行）
            sendMessage();      // 调用发送消息函数
        }
    });

    // 为上传表单添加提交事件监听器，阻止默认提交行为，改为调用uploadFile函数
    uploadForm.addEventListener('submit', function(e) {
        e.preventDefault();     // 阻止表单默认提交行为（页面刷新）
        uploadFile();           // 调用上传文件函数
    });

    // 检查用户登录状态，但不会强制重定向
    function checkUserLoginStatus() {
        const userJson = localStorage.getItem('user');
        return userJson !== null;
    }

    // 获取请求头，包含用户认证信息
    function getRequestHeaders() {
        const headers = {
            'Content-Type': 'application/json'
        };
        
        // 从本地存储获取用户信息
        const userJson = localStorage.getItem('user');
        if (userJson) {
            const user = JSON.parse(userJson);
            // 可以根据实际情况添加认证信息
            // 这里简单示例，你也可以使用token等方式
            headers['X-User-Id'] = user.userId;
            headers['X-Username'] = user.username;
        }
        
        return headers;
    }

    // 发送聊天消息的函数
    function sendMessage() {
        // 获取用户输入的消息内容并去除两端空格
        const message = userInput.value.trim();
        // 如果消息为空，则不做任何操作
        if (!message) return;

        // 检查用户是否已登录
        const userJson = localStorage.getItem('user');
        if (!userJson) {
            appendMessage('请先登录后再发送消息', 'system');
            return; // 不再强制重定向
        }

        // 将用户消息添加到聊天容器中显示
        appendMessage(message, 'user');
        // 清空输入框
        userInput.value = '';

        // 创建"正在思考"的提示元素
        const typingIndicator = document.createElement('div');
        // 设置元素的CSS类，用于样式应用
        typingIndicator.className = 'system-message typing-indicator';
        // 设置元素的文本内容
        typingIndicator.textContent = '正在思考...';
        // 将元素添加到聊天容器中
        chatContainer.appendChild(typingIndicator);
        // 滚动聊天容器到底部，显示最新消息
        chatContainer.scrollTop = chatContainer.scrollHeight;

        // 使用fetch API调用后端接口，发送用户问题
        fetch('/api/chat', {
            method: 'POST',                 // HTTP请求方法：POST
            headers: getRequestHeaders(),   // 使用带有用户认证信息的请求头
            body: JSON.stringify({ question: message })  // 将消息对象转为JSON字符串作为请求体
        })
            .then(response => {
                // 检查HTTP响应状态
                if (response.status === 401) {
                    // 未授权，需要重新登录
                    localStorage.removeItem('user');
                    throw new Error('登录已过期，请重新登录');
                }
                if (!response.ok) {
                    throw new Error('网络响应异常');  // 如果状态不是成功，抛出错误
                }
                return response.json();  // 将响应解析为JSON
            })
            .then(data => {
                // 移除"正在思考"的提示元素
                chatContainer.removeChild(typingIndicator);
                // 将系统回复添加到聊天容器
                appendMessage(data.answer, 'system');
            })
            .catch(error => {
                // 捕获并处理任何错误
                console.error('发送消息出错:', error);  // 在控制台记录错误
                // 移除"正在思考"的提示元素
                if (typingIndicator.parentNode) {
                    chatContainer.removeChild(typingIndicator);
                }
                // 显示友好的错误消息给用户
                appendMessage('抱歉，服务器暂时无法响应，请稍后再试。' + error.message, 'system');
            });
    }

    // 将消息添加到聊天容器的函数
    function appendMessage(message, sender) {
        // 创建一个新的div元素来容纳消息
        const messageElement = document.createElement('div');
        // 根据消息发送者设置不同的CSS类名
        messageElement.className = sender === 'user' ? 'user-message' : 'system-message';

        // 支持简单的Markdown格式转换为HTML
        let formattedMessage = message
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')  // 将**文本**转换为粗体
            .replace(/\n- /g, '<br>• ')                        // 将换行后的"-"转换为项目符号
            .replace(/\n/g, '<br>');                           // 将换行符转换为HTML换行标签

        // 设置消息元素的HTML内容
        messageElement.innerHTML = formattedMessage;
        // 将消息元素添加到聊天容器
        chatContainer.appendChild(messageElement);
        // 滚动聊天容器到底部，显示最新消息
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    // 上传知识库文件的函数
    function uploadFile() {
        // 检查用户是否已登录
        const userJson = localStorage.getItem('user');
        if (!userJson) {
            uploadStatus.textContent = '请先登录后再上传文件';
            uploadStatus.style.color = 'red';
            return; // 不再重定向
        }

        // 获取用户选择的文件
        const file = fileInput.files[0];
        // 如果没有选择文件，显示错误信息并返回
        if (!file) {
            uploadStatus.textContent = '请选择文件';
            uploadStatus.style.color = 'red';
            return;
        }

        // 创建FormData对象，用于发送文件
        const formData = new FormData();
        // 将文件添加到FormData中
        formData.append('file', file);

        // 获取用户信息
        const user = JSON.parse(userJson);
        // 添加用户ID到FormData
        formData.append('userId', user.userId);

        // 更新上传状态显示
        uploadStatus.textContent = '上传中...';
        uploadStatus.style.color = '#333';

        // 使用fetch API调用后端上传接口
        fetch('/api/knowledge/upload', {
            method: 'POST',     // HTTP请求方法：POST
            body: formData      // 直接发送FormData对象作为请求体
        })
            .then(response => {
                // 检查HTTP响应状态
                if (response.status === 401) {
                    // 未授权，需要重新登录
                    localStorage.removeItem('user');
                    throw new Error('登录已过期，请重新登录');
                }
                if (!response.ok) {
                    throw new Error('文件上传失败');  // 如果状态不是成功，抛出错误
                }
                return response.json();  // 将响应解析为JSON
            })
            .then(data => {
                // 上传成功，更新状态显示
                uploadStatus.textContent = '文件上传成功!';
                uploadStatus.style.color = 'green';
                // 清空文件选择输入框
                fileInput.value = '';
                // 刷新文件列表
                loadFileList();
            })
            .catch(error => {
                // 捕获并处理任何错误
                console.error('上传出错:', error);  // 在控制台记录错误
                // 显示错误消息
                uploadStatus.textContent = '上传失败: ' + error.message;
                uploadStatus.style.color = 'red';
            });
    }

    // 加载知识库文件列表的函数
    function loadFileList() {
        // 使用fetch API调用后端获取文件列表接口
        fetch('/api/knowledge/files', {
            headers: getRequestHeaders()  // 使用带有用户认证信息的请求头
        })
            .then(response => {
                // 检查HTTP响应状态
                if (response.status === 401) {
                    // 未授权，但不跳转，只显示提示
                    throw new Error('登录已过期');
                }
                if (!response.ok) {
                    throw new Error('获取文件列表失败');  // 如果状态不是成功，抛出错误
                }
                return response.json();  // 将响应解析为JSON
            })
            .then(data => {
                // 清空当前文件列表
                fileList.innerHTML = '';
                // 检查文件列表是否为空
                if (data.length === 0) {
                    // 如果为空，显示"暂无上传文件"
                    const emptyItem = document.createElement('li');
                    emptyItem.textContent = '暂无上传文件';
                    fileList.appendChild(emptyItem);
                } else {
                    // 如果不为空，遍历文件列表并显示每个文件
                    data.forEach(file => {
                        const item = document.createElement('li');
                        // 设置列表项的文本为文件名和上传时间
                        item.textContent = `${file.filename} (${formatDate(file.uploadTime)})`;
                        // 将列表项添加到文件列表
                        fileList.appendChild(item);
                    });
                }
            })
            .catch(error => {
                // 捕获并处理任何错误
                console.error('获取文件列表失败:', error);  // 在控制台记录错误
                // 显示错误消息
                fileList.innerHTML = '<li>获取文件列表失败: ' + error.message + '</li>';
            });
    }

    // 格式化日期的函数
    function formatDate(dateString) {
        // 创建一个Date对象
        const date = new Date(dateString);
        // 使用toLocaleString方法格式化日期
        return date.toLocaleString('zh-CN', {
            year: 'numeric',    // 显示年份（数字）
            month: '2-digit',   // 显示月份（两位数）
            day: '2-digit',     // 显示日期（两位数）
            hour: '2-digit',    // 显示小时（两位数）
            minute: '2-digit'   // 显示分钟（两位数）
        });
    }
});