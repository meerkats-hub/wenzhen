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
                            <span>耗时: {{ formatDuration(log.meta.total_duration) }}</span>
                            <span>{{ formatDate(log.meta.created_at) }}</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 加载状态 -->
            <!--            <div v-if="loading" class="loading-indicator">-->
            <!--                <Spin fix>-->
            <!--                    <Icon type="ios-loading" size="18" class="spin-icon-load"></Icon>-->
            <!--                    <div>AI专家思考中...</div>-->
            <!--                </Spin>-->
            <!--            </div>-->
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
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
                    :disabled="!prompt || loading"
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
    </div>
</template>

<script>
import {nextTick, onMounted, ref} from 'vue'
import {Button, Icon, Input, Message, Spin} from 'view-ui-plus'

export default {
    name: 'Main',
    components: {
        Icon, Button, Input, Spin
    },
    setup() {
        const chatContainer = ref(null)
        const historyLogs = ref([{
            question: "你好，我有些问题想咨询。",
            answer: "您好！我是AI助手，可以回答各种问题。请问您想了解什么内容？我会尽力为您提供准确、详细的解答。",
            meta: {
                model: "欢迎界面",
                created_at: new Date().toISOString()
            }
        }])
        const prompt = ref('')
        const loading = ref(false)
        const eventSource = ref(null)
        const currentAnswer = ref('')

        // 滚动到底部
        const scrollToBottom = () => {
            nextTick(() => {
                if (chatContainer.value) {
                    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
                }
            })
        }

        // 建立SSE连接
        const setupSSEConnection = (message) => {
            // 关闭之前的连接（如果有）
            if (eventSource.value) {
                eventSource.value.close()
            }

            // 添加用户问题到历史记录
            historyLogs.value.push({
                question: message,
                answer: '',
                meta: null
            })

            // 初始化当前回答
            currentAnswer.value = ''
            loading.value = true
            scrollToBottom()

            // 创建新的SSE连接
            eventSource.value = new EventSource(`http://localhost:8080/chat-stream?message=${encodeURIComponent(message)}`)

            // 处理消息事件
            eventSource.value.onmessage = (event) => {
                currentAnswer.value += event.data
                // 更新最后一条记录的AI回答
                const lastLog = historyLogs.value[historyLogs.value.length - 1]
                lastLog.answer = currentAnswer.value
                scrollToBottom()
            }

            // 处理完成事件
            eventSource.value.addEventListener('complete', () => {
                loading.value = false
                eventSource.value.close()
                Message.success('回答生成完成')

                // 更新元数据
                const lastLog = historyLogs.value[historyLogs.value.length - 1]
                lastLog.meta = {
                    model: 'gemma3:4b',
                    created_at: new Date().toISOString(),
                    total_duration: 0 // 实际项目中可以从响应中获取
                }
            })

            // 处理错误事件
            eventSource.value.onerror = (error) => {
                console.error('SSE错误:', error)
                loading.value = false
                eventSource.value.close()

                const lastLog = historyLogs.value[historyLogs.value.length - 1]
                lastLog.answer = '抱歉，回答生成失败，请稍后再试。'
                lastLog.meta = {
                    model: 'error',
                    created_at: new Date().toISOString()
                }
                Message.error('连接出错，请重试')
                scrollToBottom()
            }
        }

        // 处理聊天
        const handleChat = async () => {
            if (!prompt.value.trim() || loading.value) return
            const userQuestion = prompt.value.trim()
            prompt.value = ''
            setupSSEConnection(userQuestion)
        }

        // 清空历史记录
        const clearHistory = () => {
            // 关闭现有SSE连接
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

        // 格式化日期
        const formatDate = (isoString) => {
            return new Date(isoString).toLocaleString()
        }

        // 格式化纳秒为秒
        const formatDuration = (nanoseconds) => {
            const seconds = (nanoseconds / 1e9).toFixed(2)
            return `${seconds}秒`
        }

        // 组件卸载时关闭SSE连接
        onMounted(() => {
            scrollToBottom()
        })

        return {
            historyLogs,
            prompt,
            loading,
            chatContainer,
            handleChat,
            clearHistory,
            formatDate,
            formatDuration
        }
    }
}
</script>

<style lang="less" scoped>
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