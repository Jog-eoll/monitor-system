package com.monitorplatform.content.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentSummaryVO {
    private String contentId;
    private String contentType;
    private String status;
    private Integer isViolation;
    private String violationType;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime receiveTime;
    private String fileName;
    private String minioPath;
    private String fileUrl;
    private String thumbnail;
    private String textPreview;
    private String playBatchId;
    private Integer playBatchSeq;
    private Integer playBatchSize;
}
