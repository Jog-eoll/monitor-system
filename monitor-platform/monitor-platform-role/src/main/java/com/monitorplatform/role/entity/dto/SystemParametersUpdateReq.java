package com.monitorplatform.role.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

/*
系统模块参数更新入参

*/
@Data
public class SystemParametersUpdateReq {

    //  模块id
    @NotNull(message = "模块id不能为空")
    private Long id;

    //  模块名称
    @NotBlank(message = "模块名称不能为空")
    @Size(max = 100,message = "模块名称最长为100")
    private String name;

    //  模块编码
    @NotBlank(message = "模块编码不能为空")
    @Size(max=64,message = "模块编码最长64")
    private String code;

    //  模块参数（JSON字符串）
    private String parameters;

    //  模块创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    //  模块更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
