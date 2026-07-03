package com.monitorplatform.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("permission")
public class Permission {

    //  菜单id
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String routeUrl;

    private String pluginUrl;

    private String type;

    private Integer sort;

    private String isEnable;

    private String iconUrl;

    private Long parentId;
}
