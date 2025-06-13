<template>
    <div class="layout" style="min-height: 99vh; display: flex; flex-direction: column">
        <!-- 历史对话记录区域 -->
        <div class="chat-container" ref="chatContainer">
            <div v-for="(log, index) in historyLogs" :key="index" class="message-container">
                <!-- 用户提问 -->
                <div v-if="log.question" class="message user-message">
                    <div class="message-content">
                        <div class="message-header">
                            <Icon type="ios-person"/>
                            <span>用户</span>
                        </div>
                        <div class="message-text">{{ log.question }}</div>
                        <!-- 显示上传的图片 -->
                        <div class="image-preview-container" v-if="log.images && log.images.length > 0">
                            <div v-for="(img, imgIndex) in log.images" :key="imgIndex" class="image-preview-item">
                                <img :src="img.previewUrl" alt="上传的图片" @click="showImagePreview(img.previewUrl)"/>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- AI回答 -->
                <div v-if="log.answer" class="message ai-message">
                    <div class="message-content">
                        <div class="message-header">
                            <Icon type="ios-robot"/>
                            <span>中医AI专家</span>
                        </div>
                        <div class="message-text" v-html="log.answer"></div>
                        <div class="message-meta" v-if="log.meta">
                            <span>模型: {{ log.meta.model }}</span>
                            <span>耗时: {{ log.meta.total_duration }}</span>
                            <span>{{ log.meta.created_at }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
            <!-- 图片上传区域 -->
            <div class="image-upload-area">
                <Upload
                    ref="upload"
                    multiple
                    type="drag"
                    :before-upload="handleBeforeUpload"
                    :on-success="handleUploadSuccess"
                    :on-error="handleUploadError"
                    :on-remove="handleRemove"
                    :format="['jpg','jpeg','png','gif','webp']"
                    :max-size="5120"
                    :on-format-error="handleFormatError"
                    :on-exceeded-size="handleMaxSize"
                    :action="uploadUrl"
                    :headers="uploadHeaders"
                    :data="uploadData"
                    :show-upload-list="false"
                >
                    <div class="upload-area">
                        <Icon type="ios-cloud-upload" size="52" style="color: #3399ff"></Icon>
                        <p>点击或拖拽上传图片(最多5张)</p>
                    </div>
                </Upload>
                <div class="uploaded-images" v-if="uploadedImages.length > 0">
                    <div v-for="(img, index) in uploadedImages" :key="index" class="uploaded-image-item">
                        <img :src="img.previewUrl" alt="预览图"/>
                        <div class="image-actions">
                            <Icon type="ios-eye" @click="showImagePreview(img.previewUrl)"/>
                            <Icon type="ios-trash" @click="removeImage(index)"/>
                        </div>
                    </div>
                </div>
            </div>

            <Input
                v-model="prompt"
                type="textarea"
                :rows="4"
                placeholder="输入您的问题..."
                @keydown.enter.exact.prevent="handleChat"
                :disabled="loading"
            />
            <div class="action-buttons">
                <Button
                    type="primary"
                    @click="handleChat"
                    :loading="loading"
                    :disabled="(!prompt && uploadedImages.length === 0) || loading"
                >
                    <template #icon>
                        <Icon type="md-send"/>
                    </template>
                    发送
                </Button>
                <Button
                    @click="clearHistory"
                    :disabled="historyLogs.length <= 1 || loading"
                >
                    <template #icon>
                        <Icon type="md-trash"/>
                    </template>
                    清空记录
                </Button>
            </div>
        </div>

        <!-- 图片预览模态框 -->
        <Modal v-model="imagePreviewVisible" title="图片预览" footer-hide>
            <img :src="previewImageUrl" style="width: 100%; max-height: 70vh; object-fit: contain;"/>
        </Modal>
    </div>
</template>

<script>
import {nextTick, ref} from 'vue'
import {Button, Icon, Input, Message, Modal, Spin, Upload} from 'view-ui-plus'

export default {
    name: 'Main',
    components: {
        Icon, Button, Input, Spin, Upload, Modal
    },
    setup() {
        const chatContainer = ref(null)
        const historyLogs = ref([{
            question: "你好！",
            answer: "您好！我是AI助手，可以回答各种问题。请问您想了解什么内容？我会尽力为您提供准确、详细的解答。",
            meta: {
                model: "Gemma3:4b",
                created_at: new Date().toISOString()
            }
        }])
        const prompt = ref('')
        const loading = ref(false)
        const eventSource = ref(null)
        const currentAnswer = ref('')

        // 图片上传相关状态
        const uploadedImages = ref([])
        const uploadUrl = ref('/api/upload') // 后端上传接口
        const uploadHeaders = ref({
            'Authorization': 'Bearer ' + localStorage.getItem('token') // 如果有认证
        })
        const uploadData = ref({}) // 额外上传参数
        const imagePreviewVisible = ref(false)
        const previewImageUrl = ref('')

        // 滚动到底部
        const scrollToBottom = () => {
            nextTick(() => {
                if (chatContainer.value) {
                    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
                }
            })
        }

        // 图片预览
        const showImagePreview = (url) => {
            previewImageUrl.value = url
            imagePreviewVisible.value = true
        }

        // 上传前处理
        const handleBeforeUpload = (file) => {
            if (uploadedImages.value.length >= 5) {
                Message.warning('最多只能上传5张图片')
                return false
            }

            // 生成预览图
            const reader = new FileReader()
            reader.readAsDataURL(file)
            reader.onload = (e) => {
                uploadedImages.value.push({
                    file: file,
                    previewUrl: e.target.result,
                    uploaded: false,
                    url: null
                })
            }

            return false // 手动上传
        }

        // 上传成功处理
        const handleUploadSuccess = (response, file, fileList) => {
            const index = uploadedImages.value.findIndex(img => img.file.uid === file.uid)
            if (index !== -1) {
                uploadedImages.value[index].uploaded = true
                uploadedImages.value[index].url = response.url // 假设后端返回url字段
                Message.success('图片上传成功')
            }
        }

        // 上传错误处理
        const handleUploadError = (error, file, fileList) => {
            Message.error('图片上传失败: ' + error.message)
        }

        // 移除图片
        const removeImage = (index) => {
            uploadedImages.value.splice(index, 1)
        }

        // 格式错误处理
        const handleFormatError = (file) => {
            Message.warning('图片格式不正确，请上传jpg、jpeg、png、gif或webp格式')
        }

        // 大小限制处理
        const handleMaxSize = (file) => {
            Message.warning('图片大小不能超过5MB')
        }

        // 创建会话并建立SSE连接
        const setupSSEConnection = async (message) => {
            // 关闭之前的连接（如果有）
            if (eventSource.value) {
                eventSource.value.close()
            }

            // 添加用户问题到历史记录
            historyLogs.value.push({
                question: message,
                images: uploadedImages.value.map(img => ({
                    previewUrl: img.previewUrl,
                    url: img.url
                })),
                answer: '',
                meta: null
            })

            // 初始化当前回答
            currentAnswer.value = ''
            loading.value = true
            scrollToBottom()

            try {
                // 1. 先POST创建会话
                const response = await fetch('/api/chat/session', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        message: message,
                        images: uploadedImages.value.filter(img => img.url).map(img => img.url)
                    })
                });

                if (!response.ok) {
                    const errorText = await response.text();
                    throw new Error(`创建会话失败: ${errorText}`);
                }

                const responseText = await response.text();
                console.log('Raw response:', responseText);

                let responseData;
                try {
                    responseData = JSON.parse(responseText);
                } catch (e) {
                    throw new Error(`响应解析失败: ${e.message}`);
                }

                let sessionId;
                if (responseData.sessionId) {
                    sessionId = responseData.sessionId;
                } else {
                    const keys = Object.keys(responseData);
                    if (keys.length > 0) {
                        sessionId = keys[keys.length - 1];
                    } else {
                        throw new Error('无法从响应中获取sessionId');
                    }
                }

                console.log('Resolved sessionId:', sessionId);

                // 2. 用sessionId建立SSE连接
                eventSource.value = new EventSource(`http://localhost:8080/api/chat/stream?sessionId=${sessionId}`);

                // 处理消息事件
                eventSource.value.onmessage = (event) => {
                    currentAnswer.value += event.data
                    const lastLog = historyLogs.value[historyLogs.value.length - 1]
                    lastLog.answer = currentAnswer.value
                    scrollToBottom()
                }

                // 处理完成事件
                eventSource.value.addEventListener('complete', () => {
                    loading.value = false
                    eventSource.value.close()
                    Message.success('回答生成完成')

                    const lastLog = historyLogs.value[historyLogs.value.length - 1]
                    lastLog.meta = {
                        model: 'gemma3:4b',
                        created_at: new Date().toISOString(),
                        total_duration: 0
                    }

                    // 清空已上传的图片
                    uploadedImages.value = []
                })

                // 处理错误事件
                eventSource.value.onerror = (error) => {
                    console.error('SSE错误:', error)
                    loading.value = false
                    if (eventSource.value) eventSource.value.close()

                    const lastLog = historyLogs.value[historyLogs.value.length - 1]
                    lastLog.answer = '抱歉，回答生成失败，请稍后再试。'
                    lastLog.meta = {
                        model: 'error',
                        created_at: new Date().toISOString()
                    }
                    Message.error('连接出错，请重试')
                    scrollToBottom()
                }
            } catch (error) {
                loading.value = false
                console.error('请求失败:', error)

                const lastLog = historyLogs.value[historyLogs.value.length - 1]
                lastLog.answer = '抱歉，请求发送失败，请稍后再试。'
                lastLog.meta = {
                    model: 'error',
                    created_at: new Date().toISOString()
                }
                Message.error('请求发送失败')
                scrollToBottom()
            }
        }

        // 处理聊天
        const handleChat = async () => {
            if ((!prompt.value.trim() && uploadedImages.value.length === 0) || loading.value) return
            const userQuestion = prompt.value.trim()
            prompt.value = ''

            // 如果有图片但未上传，先上传所有图片
            const unuploadedImages = uploadedImages.value.filter(img => !img.uploaded)
            if (unuploadedImages.length > 0) {
                Message.info('正在上传图片...')
                try {
                    await Promise.all(unuploadedImages.map(img => {
                        const formData = new FormData()
                        formData.append('file', img.file)
                        return fetch(uploadUrl.value, {
                            method: 'POST',
                            headers: uploadHeaders.value,
                            body: formData
                        }).then(res => res.json())
                            .then(data => {
                                const index = uploadedImages.value.findIndex(i => i.file.uid === img.file.uid)
                                if (index !== -1) {
                                    uploadedImages.value[index].uploaded = true
                                    uploadedImages.value[index].url = data.url
                                }
                            })
                    }))
                    Message.success('图片上传完成')
                } catch (e) {
                    Message.error('部分图片上传失败')
                    return
                }
            }

            await setupSSEConnection(userQuestion)
        }

        // 清空历史记录
        const clearHistory = () => {
            if (eventSource.value) {
                eventSource.value.close()
                eventSource.value = null
            }

            historyLogs.value = [{
                question: "对话记录已清空",
                answer: "您可以开始新的对话",
                meta: {
                    model: "系统消息",
                    created_at: new Date().toISOString()
                }
            }]
        }

        return {
            historyLogs,
            prompt,
            loading,
            chatContainer,
            uploadedImages,
            uploadUrl,
            uploadHeaders,
            uploadData,
            imagePreviewVisible,
            previewImageUrl,
            handleChat,
            clearHistory,
            showImagePreview,
            handleBeforeUpload,
            handleUploadSuccess,
            handleUploadError,
            handleRemove: removeImage,
            removeImage,
            handleFormatError,
            handleMaxSize
        }
    }
}
</script>

<style scoped>
.chat-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    background-color: #f5f5f5;
}

.message-container {
    margin-bottom: 16px;
}

.message {
    max-width: 80%;
    margin-bottom: 8px;
}

.user-message {
    margin-left: auto;
    background-color: #e1f5fe;
    border-radius: 12px 12px 0 12px;
    padding: 12px;
}

.ai-message {
    margin-right: auto;
    background-color: #ffffff;
    border-radius: 12px 12px 12px 0;
    padding: 12px;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message-header {
    font-weight: bold;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
}

.message-header i {
    margin-right: 8px;
}

.message-meta {
    font-size: 12px;
    color: #888;
    margin-top: 8px;
    display: flex;
    justify-content: space-between;
}

.input-area {
    padding: 16px;
    background-color: #ffffff;
    border-top: 1px solid #e8e8e8;
}

.action-buttons {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
    gap: 8px;
}

.image-upload-area {
    margin-bottom: 16px;
}

.upload-area {
    padding: 20px;
    text-align: center;
    background-color: #fafafa;
    border: 1px dashed #d9d9d9;
    border-radius: 4px;
    cursor: pointer;
}

.upload-area:hover {
    border-color: #1890ff;
}

.uploaded-images {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
}

.uploaded-image-item {
    position: relative;
    width: 80px;
    height: 80px;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    overflow: hidden;
}

.uploaded-image-item img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.image-actions {
    position: absolute;
    top: 0;
    right: 0;
    background-color: rgba(0, 0, 0, 0.5);
    color: white;
    display: flex;
    padding: 4px;
    opacity: 0;
    transition: opacity 0.3s;
}

.uploaded-image-item:hover .image-actions {
    opacity: 1;
}

.image-actions i {
    cursor: pointer;
    margin: 0 4px;
}

.image-preview-container {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
}

.image-preview-item {
    width: 120px;
    height: 120px;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    overflow: hidden;
    cursor: pointer;
}

.image-preview-item img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.loading-indicator {
    text-align: center;
    padding: 20px;
}

.spin-icon-load {
    animation: ani-spin 1s linear infinite;
}

@keyframes ani-spin {
    from {
        transform: rotate(0deg);
    }
    to {
        transform: rotate(360deg);
    }
}

.layout {
    max-width: 900px;
    margin: 0 auto;
    padding: 20px;
    background-color: #f5f7fa;
}

.chat-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    margin-bottom: 20px;
    background-color: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);

    &::-webkit-scrollbar {
        width: 6px;
    }

    &::-webkit-scrollbar-thumb {
        background-color: #c1c1c1;
        border-radius: 3px;
    }
}

.message-container {
    margin-bottom: 20px;
}

.message {
    margin-bottom: 12px;

    .message-content {
        max-width: 80%;
        padding: 12px 16px;
        border-radius: 8px;
        position: relative;
    }

    .message-header {
        display: flex;
        align-items: center;
        font-weight: bold;
        margin-bottom: 6px;
        font-size: 14px;

        i {
            margin-right: 6px;
        }
    }

    .message-text {
        white-space: pre-wrap;
        line-height: 1.6;
    }

    .message-meta {
        margin-top: 8px;
        font-size: 12px;
        color: #999;
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
    }
}

.user-message {
    display: flex;
    justify-content: flex-end;

    .message-content {
        background-color: #e3f2fd;
        color: #1976d2;
        margin-left: auto;
    }
}

.ai-message {
    display: flex;
    justify-content: flex-start;

    .message-content {
        background-color: #f5f5f5;
        color: #333;
        margin-right: auto;
    }
}

.input-area {
    background-color: #fff;
    padding: 15px;
    border-radius: 8px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);

    :deep(.ivu-input-wrapper) {
        margin-bottom: 10px;
    }
}

.action-buttons {
    display: flex;
    gap: 10px;

    button {
        flex: 1;
    }
}

.loading-indicator {
    text-align: center;
    padding: 10px;
    color: #999;

    .spin-icon-load {
        animation: ani-spin 1s linear infinite;
        margin-bottom: 5px;
    }
}

@keyframes ani-spin {
    from {
        transform: rotate(0deg);
    }
    50% {
        transform: rotate(180deg);
    }
    to {
        transform: rotate(360deg);
    }
}

</style>
