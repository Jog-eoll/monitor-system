package com.vw.isds.register.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 资源表
 * </p>
 *
 * @author suweiming
 * @since 2020-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysResource implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String code;

    private String parent;

    @ApiModelProperty(value = "权限名称")
    private String name;

    @ApiModelProperty(value = "权限请求路径")
    private String url;

    @ApiModelProperty(value = "图标")
    private String icon;

    @ApiModelProperty(value = "打开方式（0当前页刷新、1新选项卡打开）")
    private Integer openType;

    @ApiModelProperty(value = "源（0系统或1自定义）")
    private Integer source;

    @ApiModelProperty(value = "菜单级别（0无，1一级、2二级、3三级）")
    private Integer menuLevel;

    @ApiModelProperty(value = "菜单颜色值")
    private String menuColor;

    @ApiModelProperty(value = "常用菜单：Y-是；N-否")
    private String isCommon;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：Y-显示；N-隐藏")
    private String status;

    @ApiModelProperty(value = "设备类型（camera、door、defence）")
    private String channelType;

    @ApiModelProperty(value = "类型（1应用菜单、2设备操作、3系统管理菜单）")
    private Integer type;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "后端服务ID")
    private String serviceId;

    @ApiModelProperty(value = "快捷方式（0不是、其他是）")
    private Integer isFast;

    @ApiModelProperty("创建人")
    private String createBy;

}
