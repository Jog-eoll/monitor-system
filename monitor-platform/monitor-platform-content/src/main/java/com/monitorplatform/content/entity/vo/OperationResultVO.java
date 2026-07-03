package com.monitorplatform.content.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class OperationResultVO<T> {
    private Boolean success;
    private String message;
    private T data;
    private Integer count;
    private String contentId;
    private String status;
    private Integer total;
    private List<String> fileNames;
}
