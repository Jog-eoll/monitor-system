package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.List;

/*

角色分页统一返回
*/
@Data
public class RolePageResp {
    private Long total;
    private List<RoleListItemResp> records;
}
