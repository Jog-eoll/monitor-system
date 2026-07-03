package com.monitorplatform.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("region")
public class Region {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Integer sort;

    private Long parentId;
}
