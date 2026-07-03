package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.Date;

/*

角色列表每一项（带统计字段）
*/
@Data
public class RoleListItemResp {
    private Long id;
    private String name;
    private String code;
    private String status;
    private String description;
    private Integer permissionCount;
    private Integer userCount;
    private Date createTime;
    private Date updateTime;
    private String permissionCodes;
}
