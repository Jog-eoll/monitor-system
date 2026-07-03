package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.List;

/*
分页出参

*/
@Data
public class SystemParametersPageResp {

    private Long total;
    private List<SystemParametersListItemResp> records;
}
