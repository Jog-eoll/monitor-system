package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.List;

/*
权限分页出参

*/
@Data
public class PermissionPageResp {

    private Long total; // 根节点总数

    private List<PermissionTreeNodeResp> records; // 当前页根节点及其子树

}
