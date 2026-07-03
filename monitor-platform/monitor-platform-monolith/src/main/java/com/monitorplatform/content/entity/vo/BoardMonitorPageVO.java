package com.monitorplatform.content.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class BoardMonitorPageVO {
    private List<BoardMonitorCardVO> records;
    private Long total;
    private Integer current;
    private Integer size;
    private Integer pages;
}
