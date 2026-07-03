package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
/*
用户删除入参
*/

@Data
public class AppUserDeleteReq {
    @NotNull(message = "用户id不能为空")
    private Long id;
}
