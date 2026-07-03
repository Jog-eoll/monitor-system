package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.Date;
/*
用户列表出参
*/
@Data
public class AppUserListItemResp {

    private Long id;

    private String username;

    private Long roleId;

    private String roleName;

    private String description;

    private Date createTime;

    private Date updateTime;

    private String ukeyId;

    private String employeeNo;

    private String post;

    private Integer age;

    private String gender;

    private String avatarUrl;

    private String phone;

    private Integer isAllowChange;

}
