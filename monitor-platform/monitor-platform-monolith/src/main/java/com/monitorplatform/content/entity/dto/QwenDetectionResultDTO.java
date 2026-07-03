package com.monitorplatform.content.entity.dto;

import lombok.Data;

/**
 * 通义千问检测结果DTO
 */
@Data
public class QwenDetectionResultDTO {
    
    /**
     * 判定结果（合规/违规）
     */
    private String detectionResult;

    /**
     * 本地内容审核系统原始审核结果：通过/复核/阻断。
     */
    private String auditResult;
    
    /**
     * 违规类型（无/色情/暴力/敏感信息/其他）
     */
    private String violationType;
    
    /**
     * 置信度（0-100）
     */
    private Integer confidence;

    /**
     * 违规等级（本地模型专用："高"/"中"/"低"，千问模式下为 null）
     * 用于直接映射告警等级：高→critical(严重)  中→serious(重要)  低→general(一般)
     */
    private String violationLevel;

    /**
     * 本地内容审核系统原始数据体，调用方需要细节时直接读取此字段。
     */
    private LocalAuditResultDTO.AuditData localAuditData;
    
    /**
     * 判定依据
     */
    private String reason;
    
    /**
     * 阿里云请求ID
     */
    private String requestId;
    
    /**
     * Token消耗统计
     */
    private TokenUsage tokenUsage;
    
    @Data
    public static class TokenUsage {
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
    }
}
