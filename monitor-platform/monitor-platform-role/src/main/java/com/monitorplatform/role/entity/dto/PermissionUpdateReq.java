package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/*

菜单权限更新入参

*/
@Data
public class PermissionUpdateReq {

    @NotNull(message = "id不能为空")
    private Long id;

    @Size(max = 20, message = "菜单类型长度不能超过20")
    private String type;

    @Size(max = 100, message = "名称长度不能超过100")
    private String name;

    @Size(max = 100, message = "标识长度不能超过100")
    private String code;

    @Size(max = 255, message = "路由地址长度不能超过255")
    private String routeUrl;

    @Size(max = 255, message = "组件路径长度不能超过255")
    private String pluginUrl;

    @Size(max = 255, message = "图标路径长度不能超过255")
    private String iconUrl;

    private Integer sort;

    @Size(max = 20, message = "是否启用长度不能超过20")
    private String isEnable;

    private Long parentId;

}
