package com.monitorplatform.content.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ContentIdsRequestDTO {
    private List<String> contentIds;
}
