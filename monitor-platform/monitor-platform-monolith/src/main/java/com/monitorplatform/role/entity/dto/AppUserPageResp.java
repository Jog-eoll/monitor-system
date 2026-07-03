package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.List;
/*
用户分页出参
*/

@Data
public class AppUserPageResp {

    private Long total;

    private List<AppUserListItemResp> records;

}
