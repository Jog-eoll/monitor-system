package com.monitorplatform.content.entity.dto;

import lombok.Data;

@Data
public class InsertDefaultImageRequestDTO {
    private String boardIp;
    private Integer boardPort;
    private String minioPath;
    private String operator;
}
