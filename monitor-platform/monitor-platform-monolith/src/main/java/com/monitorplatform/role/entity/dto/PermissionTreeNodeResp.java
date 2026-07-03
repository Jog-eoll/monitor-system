package com.monitorplatform.role.entity.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/*
权限节点出参
*/

@Data
public class PermissionTreeNodeResp {

    private Long id;

    private String type;

    private String name;

    private String code;

    private String routeUrl;

    private String pluginUrl;

    private String iconUrl;

    private Integer sort;

    private String isEnable;

    private Long parentId;

    private List<PermissionTreeNodeResp> children = new ArrayList<>();

}
