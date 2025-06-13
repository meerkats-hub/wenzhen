package com.meerkats.wenzhen.controller;

import com.meerkats.wenzhen.Models;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class StreamChatController {

    private static final Logger log = LoggerFactory.getLogger(StreamChatController.class);
    private final OllamaApi ollamaApi;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    @Value("${file.upload-dir:file_dir}")
    private String uploadDir;

    @Autowired
    public StreamChatController(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    /**
     * 图片上传接口
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // 确保上传目录存在
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一文件名
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            String uniqueFilename = UUID.randomUUID() + "_" + filename;

            // 保存文件
            Path targetLocation = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // 返回文件访问URL
            String fileUrl = "/api/images/" + uniqueFilename;
            log.info("file url: {}", fileUrl);
            File uploadDirFile = new File(uploadDir);
            log.info("uploadDirFile.getAbsolutePath(): {}", uploadDirFile.getAbsolutePath());
            return ResponseEntity.ok(Map.of(
                    "url", uploadDirFile.getAbsolutePath() + "/" + uniqueFilename,
                    "filename", filename,
                    "size", String.valueOf(file.getSize())
            ));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "文件上传失败"));
        }
    }

    /**
     * 图片访问接口
     */
    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG) // 根据实际类型调整
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 创建聊天会话 (POST)
     */
    @PostMapping("/chat/session")
    public Map<String, String> createChatSession(@RequestBody ChatRequest request) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ChatSession(request.getMessage(), request.getImages()));
        return Map.of("sessionId", sessionId, "message", request.getMessage());
    }

    /**
     * 流式聊天接口 (GET)
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String sessionId) {
        SseEmitter emitter = new SseEmitter(60_000L);

        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            emitter.completeWithError(new IllegalArgumentException("无效的会话ID：" + sessionId));
            return emitter;
        }
        log.info("session: {}", session);

        executor.execute(() -> {
            try {
                // 构建系统提示词，包含图片信息
                String systemPrompt = "你是一个中医问诊专家";
                if (session.images != null && !session.images.isEmpty()) {
                    systemPrompt += "。用户上传了" + session.images.size() + "张图片作为参考，" +
                            "请结合图片内容和用户问题进行分析。";
                }

                List<String> pngBase64s = session.images.stream().map(e -> {
                    try {
                        byte[] pngBytes = Files.readAllBytes(Path.of(e));
                        return new String(Base64.getEncoder().encode(pngBytes));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }).toList();
                ollamaApi.streamingChat(OllamaApi.ChatRequest.builder(Models.GEMMA3_4B)
                                .stream(true)
                                .messages(List.of(
                                        OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM)
                                                .content(systemPrompt).build(),
                                        OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
                                                .content(session.message).images(pngBase64s).build()
                                )).build())
                        .toStream()
                        .forEach(response -> {
                            try {
                                if (response.message() != null && response.message().content() != null) {
                                    emitter.send(SseEmitter.event()
                                            .data(response.message().content())
                                            .name("message"));
                                }
                                if (response.done() != null && response.done()) {
                                    emitter.send(SseEmitter.event()
                                            .name("complete")
                                            .data(""));
                                    emitter.complete();
                                    sessions.remove(sessionId); // 清理会话
                                }
                            } catch (IOException e) {
                                throw new RuntimeException("SSE发送失败", e);
                            }
                        });
            } catch (Exception e) {
                emitter.completeWithError(e);
                sessions.remove(sessionId); // 清理会话
            }
        });

        emitter.onCompletion(() -> {
            System.out.println("SSE连接完成");
            sessions.remove(sessionId); // 清理会话
        });
        emitter.onTimeout(() -> {
            System.out.println("SSE连接超时");
            sessions.remove(sessionId); // 清理会话
        });
        emitter.onError(ex -> {
            System.out.println("SSE连接错误: " + ex.getMessage());
            sessions.remove(sessionId); // 清理会话
        });

        return emitter;
    }

    // 会话数据类
    private static class ChatSession {
        String message;
        List<String> images;

        public ChatSession(String message, List<String> images) {
            this.message = message;
            this.images = images;
        }

        @Override
        public String toString() {
            return "ChatSession{" +
                    "message='" + message + '\'' +
                    ", images=" + images +
                    '}';
        }
    }

    // 请求DTO
    public static class ChatRequest {
        private String message;
        private List<String> images; // 图片URL列表

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getImages() {
            return images;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }

        @Override
        public String toString() {
            return "ChatRequest{" +
                    "message='" + message + '\'' +
                    ", images=" + images +
                    '}';
        }
    }
}