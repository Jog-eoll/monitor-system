package com.monitorplatform.content.entity.dto;

import lombok.Data;

@Data
public class DeleteByConditionRequestDTO {
    private String status;
    private Integer isViolation;
}
