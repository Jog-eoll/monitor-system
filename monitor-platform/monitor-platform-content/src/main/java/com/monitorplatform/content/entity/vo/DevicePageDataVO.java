package com.monitorplatform.content.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class DevicePageDataVO {
    private List<DeviceInfoVO> records;
    private Long total;
    private Long current;
    private Long size;
    private Long pages;
}
