package com.monitorplatform.content.entity.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * 本地视觉审核模型响应DTO
 * 
 * 对应内网模型: http://127.0.0.1:8080/api/audit/image
 * 输出格式: 结构化JSON(无需正则解析)
 */
@Data
public class LocalAuditResultDTO {

    /** 状态码: 0=成功 */
    @JSONField(name = "状态码", alternateNames = {"code"})
    private Integer code;

    /** 失败提示信息 */
    @JSONField(name = "消息", alternateNames = {"message", "msg"})
    private String message;

    /** 审核结果数据 */
    @JSONField(name = "数据", alternateNames = {"data"})
    private AuditData data;

    @Data
    public static class AuditData {

        /** 审核结果: "通过"/"复核"/"阻断" */
        @JSONField(name = "审核结果", alternateNames = {"audit_result", "decision"})
        private String auditResult;

        /** 违规等级: "无"/"低"/"中"/"高" */
        @JSONField(name = "违规等级", alternateNames = {"violation_level", "riskLevel"})
        private String violationLevel;

        /** 目标检测结果 */
        @JSONField(name = "检测物体", alternateNames = {"detections"})
        private List<Detection> detections;

        /** OCR文字识别结果 */
        @JSONField(name = "识别文字", alternateNames = {"ocr_texts"})
        private List<OcrText> ocrTexts;

        /** 完整文本(OCR拼接) */
        @JSONField(name = "完整文字", alternateNames = {"full_text"})
        private String fullText;

        /** 违规详情列表 */
        @JSONField(name = "违规详情", alternateNames = {"violations"})
        private List<Violation> violations;

        /** CLIP场景分析 */
        @JSONField(name = "CLIP分析", alternateNames = {"clip_analysis"})
        private ClipAnalysis clipAnalysis;

        /** 统计摘要 */
        @JSONField(name = "统计摘要", alternateNames = {"summary"})
        private Summary summary;

        /** 时间戳 */
        @JSONField(name = "检测时间", alternateNames = {"timestamp"})
        private String timestamp;
    }

    @Data
    public static class Detection {
        /** 类别ID */
        @JSONField(name = "类别ID", alternateNames = {"class_id"})
        private Integer classId;

        /** 类别名称(人/雨伞/车辆等) */
        @JSONField(name = "类别名称", alternateNames = {"class_name"})
        private String className;

        /** 置信度 0-1 */
        @JSONField(name = "置信度", alternateNames = {"confidence"})
        private Double confidence;

        /** 边界框 [x1, y1, x2, y2] */
        @JSONField(name = "边界框", alternateNames = {"bbox"})
        private List<Double> bbox;
    }

    @Data
    public static class OcrText {
        /** 识别文本 */
        @JSONField(name = "文字内容", alternateNames = {"text"})
        private String text;

        /** 置信度 0-1 */
        @JSONField(name = "置信度", alternateNames = {"confidence"})
        private Double confidence;

        /** 文字位置坐标 [[x1,y1],[x2,y2],[x3,y3],[x4,y4]] */
        @JSONField(name = "位置", alternateNames = {"position"})
        private List<List<Double>> position;
    }

    @Data
    public static class Violation {
        /** 违规类型: "场景违规"/"文字违规"/"物体违规" */
        @JSONField(name = "违规类型", alternateNames = {"type"})
        private String type;

        /** 违规文本 */
        @JSONField(name = "text")
        private String text;

        /** 命中的关键词 */
        @JSONField(name = "keyword")
        private String keyword;

        /** 命中次数 */
        @JSONField(name = "出现次数", alternateNames = {"count"})
        private Integer count;

        /** 场景类型: "人员密集"/"疑似打架"等 */
        @JSONField(name = "场景类型", alternateNames = {"scene_type"})
        private String sceneType;

        /** 置信度 0-1 */
        @JSONField(name = "置信度", alternateNames = {"confidence"})
        private Double confidence;

        /** 违规等级: "无"/"低"/"中"/"高" */
        @JSONField(name = "违规等级", alternateNames = {"violation_level"})
        private String violationLevel;

        /** 违规原因说明 */
        @JSONField(name = "违规原因", alternateNames = {"violation_reason"})
        private String violationReason;

        /** CLIP分析详情 */
        @JSONField(name = "clip_details")
        private ClipDetails clipDetails;
    }

    @Data
    public static class ClipDetails {
        /** 打架分数 */
        @JSONField(name = "打架得分", alternateNames = {"fight_score"})
        private Double fightScore;

        /** 暴力分数 */
        @JSONField(name = "暴力得分", alternateNames = {"violence_score"})
        private Double violenceScore;

        /** 正常分数 */
        @JSONField(name = "正常得分", alternateNames = {"normal_score"})
        private Double normalScore;

        /** 匹配提示词列表 */
        @JSONField(name = "匹配描述", alternateNames = {"top_prompts"})
        private List<PromptScore> topPrompts;
    }

    @Data
    public static class PromptScore {
        /** 提示词 */
        @JSONField(name = "描述", alternateNames = {"prompt"})
        private String prompt;

        /** 匹配分数 */
        @JSONField(name = "得分", alternateNames = {"score"})
        private Double score;
    }

    @Data
    public static class ClipAnalysis {
        @JSONField(name = "打架得分", alternateNames = {"fight_score"})
        private Double fightScore;

        @JSONField(name = "暴力得分", alternateNames = {"violence_score"})
        private Double violenceScore;

        @JSONField(name = "正常得分", alternateNames = {"normal_score"})
        private Double normalScore;

        @JSONField(name = "匹配描述", alternateNames = {"top_prompts"})
        private List<PromptScore> topPrompts;
    }

    @Data
    public static class Summary {
        /** 检测到的目标总数 */
        @JSONField(name = "物体总数", alternateNames = {"total_objects"})
        private Integer totalObjects;

        /** 识别的文本总数 */
        @JSONField(name = "文字总数", alternateNames = {"total_texts"})
        private Integer totalTexts;

        /** 物体违规数 */
        @JSONField(name = "物体违规数", alternateNames = {"object_violations"})
        private Integer objectViolations;

        /** 文字违规数 */
        @JSONField(name = "文字违规数", alternateNames = {"text_violations"})
        private Integer textViolations;

        /** 场景违规数 */
        @JSONField(name = "场景违规数", alternateNames = {"scene_violations"})
        private Integer sceneViolations;

        /** 违禁图片违规数 */
        @JSONField(name = "违禁图片违规数", alternateNames = {"forbidden_image_violations"})
        private Integer forbiddenImageViolations;

        /** 总违规数 */
        @JSONField(name = "违规总数", alternateNames = {"total_violations"})
        private Integer totalViolations;
    }
}
