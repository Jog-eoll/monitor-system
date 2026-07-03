package com.monitorplatform.log.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResultVO {
    private String eventId;
    private Boolean deduplicated;
}
