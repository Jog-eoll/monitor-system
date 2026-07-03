package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdBatchRequestDTO {
    private List<Long> ids;
}
