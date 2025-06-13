package com.meerkats.wenzhen.controller;

import com.meerkats.wenzhen.Models;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
public class ReportController {

    @Autowired
    private OllamaApi ollamaApi;

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    // 存储用户上传的多媒体文件，key为sessionId
    private final Map<String, Map<String, byte[]>> multipleMedias = new HashMap<>();

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("faceImg") MultipartFile faceImg,
            @RequestParam("tongue") MultipartFile tongue,
            @RequestParam("audio") MultipartFile audio) {
        log.info("开始上传：");
        // 验证文件是否为空
        if (faceImg.isEmpty() || tongue.isEmpty() || audio.isEmpty()) {
            throw new IllegalArgumentException("请上传所有必需的文件");
        }

        // 验证文件类型
        validateFileType(faceImg, "image/jpeg", "image/png");
        validateFileType(tongue, "image/jpeg", "image/png");
        validateFileType(audio, "audio/mpeg", "audio/aac");

        // 生成唯一sessionId
        String sessionId = UUID.randomUUID().toString();

        try {
            // 存储文件数据
            Map<String, byte[]> mediaMap = new HashMap<>();
            mediaMap.put("faceImg", faceImg.getBytes());
            mediaMap.put("tongue", tongue.getBytes());
            mediaMap.put("audio", audio.getBytes());

            multipleMedias.put(sessionId, mediaMap);


            // 生成报告
            String report = getReport(sessionId);
            OllamaApi.ChatResponse chatResponse = getAiResult("你是一名中医问诊专家，擅长通过观看面部和舌苔来诊断青少年是否有抑郁倾向",
                    """
                            对于面部图片，判断此青少年是否存在中医“郁证”（情志抑郁）相关的典型面相表现。
                            
                            以下为“郁证面相”常见特征：
                            - 面色晦暗或偏灰、缺乏光泽；
                            - 神情呆滞、目光无神；
                            - 表情僵硬、不自然、缺乏情绪反应；
                            - 面部肌肉紧绷或有轻微偏斜；
                            - 眼睑沉重或眼神躲避。
                            
                            如图像中*明显存在一个及以上*上述特征，输出`面部：1`（表示有郁证面相）。 \s
                            如上述特征基本未见或图像不清晰，输出`面部：0`。 \s
                            
                            
                            对于舌苔图片，你作为一名具备中医舌诊知识的AI医生，依据以下舌象图像，判断是否存在中医“气郁质”或“血瘀质”相关的典型舌象表现。
                            
                            重点观察以下特征：
                            
                            1. 舌体颜色偏紫或暗红；
                            2. 舌面存在黑点或紫斑（瘀点）；
                            3. 舌尖偏尖、舌形略呈尖细（多见于气郁）；
                            4. 舌苔薄白，边缘可能略有齿痕；
                            
                            如图像中出现*任意一项以上表现*，说明该青少年可能存在气郁或血瘀体质倾向，输出 `舌苔：1`；
                            如以上表现均未出现或图像质量不足以判断，输出 `舌苔：0`。
                            """, List.of(
                            new String(Base64.getEncoder().encode(faceImg.getBytes())),
                            new String(Base64.getEncoder().encode(tongue.getBytes()))
                    ));

            // 返回结果
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("sessionId", sessionId);
            response.put("reportUrl", "/report/" + sessionId);
            response.put("reportContent", chatResponse.message().content());
            log.info("生成完成");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            throw new RuntimeException("文件处理失败", e);
        }
    }

    private void validateFileType(MultipartFile file, String... allowedTypes) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("无法识别文件类型: " + file.getOriginalFilename());
        }

        for (String allowedType : allowedTypes) {
            if (contentType.equals(allowedType)) {
                return;
            }
        }

        throw new IllegalArgumentException("不支持的文件类型: " + contentType +
                " 仅支持: " + String.join(", ", allowedTypes));
    }

    public String getReport(String sessionId) {
        // 从存储中获取文件数据
        Map<String, byte[]> mediaMap = multipleMedias.get(sessionId);
        if (mediaMap == null) {
            throw new IllegalArgumentException("无效的sessionId");
        }

        // 这里先返回固定文本报告，后期可以替换为大模型调用
        StringBuilder report = new StringBuilder();
        report.append("青少年心理分析报告\n\n");
        report.append("基于您提交的资料，初步分析如下：\n");
        report.append("- 面部表情分析: 中性情绪，无明显抑郁特征\n");
        report.append("- 舌苔分析: 健康状况良好，无显著异常\n");
        report.append("- 语音分析: 语速适中，语调平稳，情绪稳定\n\n");
        report.append("综合评估: 心理状态处于正常范围，建议保持健康作息和社交活动。\n");
        report.append("如需进一步评估，请咨询专业心理医生。");

        return report.toString();
    }


    /**
     * 与大模型对话接口
     *
     * @param userMessage 用户输入的文本
     * @return 大模型生成的回复
     */
    public OllamaApi.ChatResponse getAiResult(String systemMessage, String userMessage, List<String> images) {
        // 构建Prompt并调用模型
        // 提取模型返回内容
        return ollamaApi
                .chat(OllamaApi.ChatRequest.builder(Models.GEMMA3_4B).messages(
                        List.of(
                                OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM)
                                        .content(systemMessage).build(),
                                OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
                                        .content(userMessage)
                                        .images(images)
                                        .build()
                        )
                ).build());
    }

    String templateExample = """
                    **面部分析：**
                    
                    根据图片，这位青少年面部表情略带笑容，眼神明亮，神情活泼。虽然面部表情略有僵硬，但整体上没有明显的呆滞、眼神无神、表情僵硬、不自然、缺乏情绪反应、面部肌肉紧绷或有轻微偏斜、眼睑沉重或眼神躲避等“郁证面相”特征。
                    
                    因此，输出：**面部：0**
                    
                    **舌苔分析：**
                    
                    根据图片，舌苔看起来是白色且薄，没有明显的紫斑、黑点或齿痕。
                    
                    因此，输出：**舌苔：0**
                    """;
    String soundPrompt = """
                    作为具备中医知识的AI医生，通过分析用户语音特征，结合中医闻诊理论与抑郁症临床表现，判断声音主人是否存在抑郁倾向。
                    需重点分析的语音特征：
                    1、语速与流畅度
                    是否出现明显语速迟缓、应答延迟？
                    语句是否频繁中断、停顿过长（>2秒）或逻辑断裂？
                    中医辨证参考： 气滞痰阻，心神不宁。
                    2、音调与情感饱和度
                    音调是否长期维持单调平直（缺乏抑扬顿挫）？
                    是否呈现“情感钝化”特征（如笑声僵硬、缺乏波动）？
                    中医辨证参考： 肝气郁结，情志不舒。
                    3、气息与发声力度
                    是否气息微弱、声音轻飘（似无力发声）？
                    是否频繁出现深吸气/长叹气（≥3次/分钟）？
                    中医辨证参考： 宗气不足，脾虚肺弱。
                    4、语言内容特征
                    是否反复使用消极词汇（如“累”“绝望”“无意义”）？
                    是否自我否定表述占比超30%（如“我不行”“都是我的错”）？
                    
                    抑郁倾向等级：判定标准                     \s
                    高度可能：满足≥3项特征，且语言消极占比显著  \s
                    中度可能 ： 满足2项特征，伴随气息/语速异常
                    轻度可能：满足1项特征，无明确消极语言
                    无倾向：无上述特征或可归因于生理因素（如感冒）
                    根据上面四点，给出综合评判，声音的特征，是否有抑郁症倾向。""";
}