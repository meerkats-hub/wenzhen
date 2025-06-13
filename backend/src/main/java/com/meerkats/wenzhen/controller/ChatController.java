package com.meerkats.wenzhen.controller;

import com.meerkats.wenzhen.Models;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RestController
public class ChatController {

    private final OllamaApi ollamaApi;

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public ChatController(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    /**
     * 与大模型对话接口
     *
     * @param message 用户输入的文本
     * @return 大模型生成的回复
     */
    @PostMapping("/chat")
    public OllamaApi.ChatResponse chat(String systemMessage, @RequestBody String message) {
        // 构建Prompt并调用模型
        Prompt prompt = new Prompt(message);
        OllamaApi.ChatResponse response = ollamaApi
                .chat(OllamaApi.ChatRequest.builder(Models.GEMMA3_4B).messages(
                        List.of(
                                OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM).content("你是一个中医问诊专家").build(),
                                OllamaApi.Message.builder(OllamaApi.Message.Role.USER).content(message).build()
                        )
                ).build());
        // 提取模型返回内容
        return response;
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message) {
        SseEmitter emitter = new SseEmitter(60_000L);

        executor.execute(() -> {
            try {
                // 假设ollamaApi.chatStream返回一个流式响应
                ollamaApi.streamingChat(OllamaApi.ChatRequest.builder(Models.GEMMA3_4B)
                        .stream(true)
                        .messages(
                                List.of(
                                        OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM)
                                                .content("你是一个中医问诊专家").build(),
                                        OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
                                                .content(message).build()
                                )
                        ).build()).toStream().forEach(response -> {
                    try {
                        // 发送消息内容
                        if (response.message() != null && response.message().content() != null) {
                            emitter.send(SseEmitter.event()
                                    .data(response.message().content())
                                    .name("message"));
                        }

                        // 如果对话完成，发送完成事件
                        if (response.done() != null && response.done()) {
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(""));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("SSE发送失败", e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        // 连接生命周期回调
        emitter.onCompletion(() -> System.out.println("SSE连接完成"));
        emitter.onTimeout(() -> System.out.println("SSE连接超时"));
        emitter.onError(ex -> System.out.println("SSE连接错误: " + ex.getMessage()));
        return emitter;
    }

}