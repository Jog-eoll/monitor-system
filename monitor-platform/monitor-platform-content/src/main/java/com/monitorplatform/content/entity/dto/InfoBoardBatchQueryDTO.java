package com.monitorplatform.content.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class InfoBoardBatchQueryDTO {
    private List<String> deviceIds;
}
