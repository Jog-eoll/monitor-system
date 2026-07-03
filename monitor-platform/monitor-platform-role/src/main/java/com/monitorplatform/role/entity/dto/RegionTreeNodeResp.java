package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/*
区域树节点出参

*/
@Data
public class RegionTreeNodeResp {

    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Long parentId;
    private List<RegionTreeNodeResp> children = new ArrayList<>();

}
