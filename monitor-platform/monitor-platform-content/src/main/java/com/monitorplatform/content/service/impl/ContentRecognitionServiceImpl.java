package com.monitorplatform.content.service.impl;

import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import com.monitorplatform.content.entity.dto.RecognitionResultDTO;
import com.monitorplatform.content.service.ContentRecognitionService;
import com.monitorplatform.content.service.LocalAuditService;
import com.monitorplatform.content.service.QwenApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内容识别服务实现
 * 使用DFA算法进行敏感词检测
 */
@Slf4j
@Service
public class ContentRecognitionServiceImpl implements ContentRecognitionService {

    @Autowired
    private QwenApiService qwenApiService;

    @Autowired
    private LocalAuditService localAuditService;

    /** AI检测模式: qwen(千问公网) / local(内网本地模型) */
    @Value("${audit.mode:qwen}")
    private String auditMode;

    /** DFA敏感词字典树 */
    private Map<Character, Object> sensitiveWordMap = new ConcurrentHashMap<>();

    /** 默认敏感词列表 */
    private static final List<String> DEFAULT_SENSITIVE_WORDS = Arrays.asList(
            // 测试敏感词
            "违规内容", "敏感词A", "敏感词B", "违法信息", "非法内容",
            "暴力", "色情", "赌博", "诈骗", "传销",
            "恐怖", "毒品", "枪支", "反动", "邪教"
    );

    @PostConstruct
    public void init() {
        log.info("初始化敏感词库...");
        reloadSensitiveWords();
    }

    @Override
    public RecognitionResultDTO recognize(String contentId, String contentType, String data) {
        long startTime = System.currentTimeMillis();
        RecognitionResultDTO result = new RecognitionResultDTO();
        result.setContentId(contentId);

        try {
            String textContent;
            
            if ("image".equals(contentType)) {
                // 图片内容：模拟OCR提取文字
                // 实际项目中应调用OCR服务（如百度OCR、阿里云OCR等）
                textContent = simulateOcr(data);
                result.setOcrText(textContent);
                log.info("OCR提取文字: contentId={}, text={}", contentId, 
                        textContent.length() > 100 ? textContent.substring(0, 100) + "..." : textContent);
            } else {
                // 文本内容：直接使用
                textContent = data;
            }

            // 第一步：DFA快速敏感词检测
            RecognitionResultDTO detectResult = detectSensitiveWords(contentId, textContent);
            result.setIsViolation(detectResult.getIsViolation());
            result.setViolationType(detectResult.getViolationType());
            result.setConfidence(detectResult.getConfidence());
            result.setKeywords(detectResult.getKeywords());

            // 第二步：文本内容额外调用大模型深度检测（仅 qwen 模式生效，DFA未命中时仍需大模型兄底）
            if ("text".equals(contentType) && !"local".equalsIgnoreCase(auditMode)) {
                try {
                    QwenDetectionResultDTO qwenResult = qwenApiService.detectTextContent(
                            textContent, contentId, contentId);
                    log.info("千问文本检测结果: contentId={}, result={}, violationType={}, confidence={}",
                            contentId, qwenResult.getDetectionResult(),
                            qwenResult.getViolationType(), qwenResult.getConfidence());
            
                    // 千问或DFA任一认为违规则判定违规（取最严格结果）
                    boolean qwenViolation = "violation".equals(qwenResult.getDetectionResult());
                    if (qwenViolation) {
                        result.setIsViolation(true);
                        result.setViolationType(qwenResult.getViolationType());
                        result.setConfidence(qwenResult.getConfidence() / 100.0);
                    } else if (Boolean.FALSE.equals(result.getIsViolation())) {
                        // 千问也认为合规，更新置信度为千问的结果
                        result.setConfidence(qwenResult.getConfidence() / 100.0);
                    }
                } catch (Exception e) {
                    log.warn("千问文本检测失败，仅使用DFA结果: contentId={}, error={}", contentId, e.getMessage());
                }
            } else if ("text".equals(contentType) && "local".equalsIgnoreCase(auditMode)) {
                // local 模式：调用本地模型文本审核接口，模型结果即最终结果
                try {
                    QwenDetectionResultDTO localResult = localAuditService.auditText(textContent, contentId, contentId);
                    log.info("本地模型文本检测结果: contentId={}, result={}, violationType={}, confidence={}",
                            contentId, localResult.getDetectionResult(),
                            localResult.getViolationType(), localResult.getConfidence());

                    applyLocalAuditResult(result, localResult);
                } catch (Exception e) {
                    log.warn("本地模型文本检测失败: contentId={}, error={}", contentId, e.getMessage());
                    result.setIsViolation(null);
                    result.setViolationType("other");
                    result.setConfidence(0.0);
                    result.setKeywords(Collections.singletonList("local audit text detect failed: " + e.getMessage()));
                }
            }

        } catch (Exception e) {
            log.error("内容识别失败: contentId={}", contentId, e);
            result.setIsViolation(false);
            result.setConfidence(0.0);
        }

        result.setRecognitionTime(System.currentTimeMillis() - startTime);
        return result;
    }

    private void applyLocalAuditResult(RecognitionResultDTO result, QwenDetectionResultDTO localResult) {
        if (localResult == null) {
            result.setIsViolation(null);
            result.setViolationType("other");
            result.setConfidence(0.0);
            result.setKeywords(Collections.singletonList("local audit result is null"));
            return;
        }

        result.setViolationType(localResult.getViolationType());
        result.setAuditResult(localResult.getAuditResult());
        result.setViolationLevel(localResult.getViolationLevel());
        result.setConfidence(localResult.getConfidence() != null ? localResult.getConfidence() / 100.0 : 0.0);
        result.setKeywords(localResult.getReason() != null && !localResult.getReason().trim().isEmpty()
                ? Collections.singletonList(localResult.getReason()) : Collections.emptyList());

        String detectionResult = localResult.getDetectionResult();
        if ("violation".equals(detectionResult)) {
            result.setIsViolation(true);
        } else if ("pending".equals(detectionResult)) {
            result.setIsViolation(null);
        } else {
            result.setIsViolation(false);
        }
    }

    @Override
    public RecognitionResultDTO detectSensitiveWords(String contentId, String text) {
        RecognitionResultDTO result = new RecognitionResultDTO();
        result.setContentId(contentId);

        if (text == null || text.isEmpty()) {
            result.setIsViolation(false);
            result.setConfidence(0.0);
            result.setKeywords(Collections.emptyList());
            return result;
        }

        // 使用DFA算法检测敏感词
        Set<String> foundWords = searchSensitiveWords(text);

        if (foundWords.isEmpty()) {
            result.setIsViolation(false);
            result.setConfidence(0.05);  // 低置信度表示正常
            result.setKeywords(Collections.emptyList());
            log.info("未检测到敏感词: contentId={}", contentId);
        } else {
            result.setIsViolation(true);
            result.setViolationType("敏感词违规");
            result.setConfidence(0.95);  // 高置信度表示违规
            result.setKeywords(new ArrayList<>(foundWords));
            log.warn("检测到敏感词: contentId={}, keywords={}", contentId, foundWords);
        }

        return result;
    }

    @Override
    public void reloadSensitiveWords() {
        // 构建DFA字典树
        Map<Character, Object> newMap = new ConcurrentHashMap<>();
        
        for (String word : DEFAULT_SENSITIVE_WORDS) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            Map<Character, Object> currentMap = newMap;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                Object obj = currentMap.get(c);
                if (obj == null) {
                    Map<Character, Object> nextMap = new HashMap<>();
                    nextMap.put('$', false);  // 结束标志
                    currentMap.put(c, nextMap);
                    currentMap = nextMap;
                } else {
                    currentMap = (Map<Character, Object>) obj;
                }
            }
            currentMap.put('$', true);  // 标记词结束
        }
        
        sensitiveWordMap = newMap;
        log.info("敏感词库已加载，共{}个词", DEFAULT_SENSITIVE_WORDS.size());
    }

    /**
     * DFA算法搜索敏感词
     */
    @SuppressWarnings("unchecked")
    private Set<String> searchSensitiveWords(String text) {
        Set<String> foundWords = new HashSet<>();
        
        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i);
            if (length > 0) {
                foundWords.add(text.substring(i, i + length));
                i = i + length - 1;  // 跳过已匹配的词
            }
        }
        
        return foundWords;
    }

    /**
     * 检查从指定位置开始是否有敏感词
     * @return 敏感词长度，0表示没有
     */
    @SuppressWarnings("unchecked")
    private int checkSensitiveWord(String text, int beginIndex) {
        Map<Character, Object> currentMap = sensitiveWordMap;
        int matchLength = 0;
        int maxMatchLength = 0;

        for (int i = beginIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            Object obj = currentMap.get(c);
            
            if (obj == null) {
                break;
            }
            
            currentMap = (Map<Character, Object>) obj;
            matchLength++;
            
            if (Boolean.TRUE.equals(currentMap.get('$'))) {
                maxMatchLength = matchLength;  // 记录最长匹配
            }
        }

        return maxMatchLength;
    }

    /**
     * 模拟OCR文字提取
     * 实际项目中应调用真正的OCR服务
     */
    private String simulateOcr(String base64Data) {
        // 这里简单模拟，返回一个测试文本
        // 实际应用中应该：
        // 1. 将base64解码为图片
        // 2. 调用OCR API（如Tesseract、百度OCR、阿里云OCR等）
        // 3. 返回识别出的文字
        
        if (base64Data == null || base64Data.isEmpty()) {
            return "";
        }
        
        // 模拟返回，实际部署时替换为真正的OCR调用
        return "OCR模拟提取文本内容";
    }
}
