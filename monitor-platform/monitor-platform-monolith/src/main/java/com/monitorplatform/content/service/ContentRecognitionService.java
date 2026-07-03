package com.monitorplatform.content.service;

import com.monitorplatform.content.entity.dto.RecognitionResultDTO;

/**
 * 内容识别服务接口
 * 提供敏感词检测、OCR文字提取等功能
 */
public interface ContentRecognitionService {

    /**
     * 识别内容
     * @param contentId 内容ID
     * @param contentType 内容类型：image/text
     * @param data 内容数据（base64或文本）
     * @return 识别结果
     */
    RecognitionResultDTO recognize(String contentId, String contentType, String data);

    /**
     * 敏感词检测
     * @param text 文本内容
     * @return 识别结果
     */
    RecognitionResultDTO detectSensitiveWords(String contentId, String text);

    /**
     * 重新加载敏感词库
     */
    void reloadSensitiveWords();
}
