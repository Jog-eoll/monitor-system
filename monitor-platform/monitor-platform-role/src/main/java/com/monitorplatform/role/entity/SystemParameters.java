package com.monitorplatform.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_parameters")
public class SystemParameters {

    //  模块id
    @TableId(type = IdType.AUTO)
    private Long id;

    //  模块名称
    private String name;

    //  模块编码
    private String code;

    //  模块参数（JSON字符串）
    private String parameters;

    //  模块创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    //  模块更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime  updateTime;
}
