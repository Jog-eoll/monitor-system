package com.monitorplatform.common.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(description = "系统管理-操作日志vo")
@Data
public class SysUserLogVO {

    @ApiModelProperty(value = "主键ID")
    private String id;

    @ApiModelProperty(value = "操作人员编号")
    private String userId;

    @ApiModelProperty(value = "归属应用")
    private String appCode;

    @ApiModelProperty(value = "操作人名称")
    private String actorName;

    @ApiModelProperty(value = "日志类型  system :系统；  operation:操作")
    private String type;

    @ApiModelProperty(value = "操作类型, other : 其它; add : 新增 ; update : 修改 ; delete : 删除; query : 查询; login : 用户登录; logout: 用户登出")
    private String actType;

    @ApiModelProperty(value = "操作模块")
    private String actModule;

    @ApiModelProperty(value = "操作功能")
    private String actAction;

    @ApiModelProperty(value = "操作结果")
    private String actResult;

    @ApiModelProperty(value = "操作对象")
    private String actObj;

    @ApiModelProperty(value = "操作信息")
    private String actMessage;

    @ApiModelProperty(value = "操作ip信息")
    private String clientIp;

    @ApiModelProperty(value = "日志埋点id")
    private String pointId;
}
