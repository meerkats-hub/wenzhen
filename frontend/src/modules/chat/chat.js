import {rest} from './base.js'

export const chat = (message) => {
    return rest.request({
        url: 'chat',
        method: 'post',
        data: message
    })
}

/**
 * 建立SSE连接进行发送与接收消息
 *
 * @param message 要发送的消息
 */
export const setupSSEConnection = (message) => {
    const eventSource = new EventSource(`/chat-stream?message=${encodeURIComponent(message)}`);
    const chatOutput = document.getElementById('chat-output');

    eventSource.onmessage = (event) => {
        // 追加新消息到聊天界面
        chatOutput.innerHTML += `<div class="ai-message">${event.data}</div>`;
        chatOutput.scrollTop = chatOutput.scrollHeight;
    };

    eventSource.addEventListener('complete', () => {
        console.log('对话完成');
        eventSource.close();
    });

    eventSource.onerror = (error) => {
        console.error('SSE错误:', error);
        eventSource.close();
        chatOutput.innerHTML += `<div class="error-message">连接出错，请重试</div>`;
    };
}