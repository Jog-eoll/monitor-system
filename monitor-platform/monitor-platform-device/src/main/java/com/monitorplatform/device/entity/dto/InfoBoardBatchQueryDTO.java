package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class InfoBoardBatchQueryDTO {

    @NotEmpty(message = "deviceIds cannot be empty")
    private List<String> deviceIds;
}
