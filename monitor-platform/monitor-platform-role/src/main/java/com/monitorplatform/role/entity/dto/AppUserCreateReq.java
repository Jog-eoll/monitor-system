package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
/*
用户创建入参

*/

@Data
public class AppUserCreateReq {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 100,message = "用户名最长100")
    private String username;

    @Size(max = 255,message = "密码最长255")
    private String password;

    private Long roleId;

    @Size(max = 255,message = "描述最长255")
    private String description;

    @Size(max = 128,message = "ukeyId最长128")
    private String ukeyId;

    @Size(max = 64, message = "工号最长64")
    private String employeeNo;

    @Size(max = 64, message = "岗位最长64")
    private String post;

    private Integer age;

    @Size(max = 16, message = "性别最长16")
    private String gender;

    @Size(max = 512, message = "头像URL最长512")
    private String avatarUrl;

    @Size(max = 32, message = "手机号最长32")
    private String phone;

    /** 1=可修改, 0=不可修改; 不传默认1 */
    private Integer isAllowChange;
}
