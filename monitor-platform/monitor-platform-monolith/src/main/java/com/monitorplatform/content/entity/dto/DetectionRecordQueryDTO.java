package com.monitorplatform.content.entity.dto;

import lombok.Data;

/**
 * 检测记录查询DTO
 */
@Data
public class DetectionRecordQueryDTO {
    
    private String deviceId;
    
    private String detectionResult;
    
    private String violationType;
    
    private String status;
    
    private String startTime;
    
    private String endTime;
    
    private Integer pageNum = 1;
    
    private Integer pageSize = 10;
}
