package com.monitorplatform.content.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 识别结果DTO
 */
@Data
public class RecognitionResultDTO {

    /** 内容ID */
    private String contentId;

    /** 是否违规 */
    private Boolean isViolation;

    /** 违规类型 */
    private String violationType;

    /** 本地模型原始审核结果：通过/复核/阻断 */
    private String auditResult;

    /** 本地模型违规等级：无/低/中/高 */
    private String violationLevel;

    /** 识别置信度（0-1） */
    private Double confidence;

    /** 命中的敏感词列表 */
    private List<String> keywords;

    /** OCR提取的文字内容 */
    private String ocrText;

    /** 识别耗时(ms) */
    private Long recognitionTime;
}
