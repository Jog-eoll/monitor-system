package com.monitorplatform.content.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class BoardMonitorListQueryDTO {
    private Integer pageNum;
    private Integer pageSize;
    /*
        情报板ip筛选
     */
    private String ipKeyword;
    private List<String> cardIdList;
}
