package com.monitorplatform.role.entity;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统管理-操作日志表
 * </p>
 *
 * @author suweiming
 * @since 2022-07-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
@ExcelIgnoreUnannotated
@ApiModel(value = "SysUserLog对象", description = "系统管理-操作日志表")
public class SysUserLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @ApiModelProperty(value = "操作人员编号")
    private String userId;

    @ApiModelProperty(value = "归属应用")
    private String appCode;

    @ApiModelProperty(value = "操作人名称")
    @ExcelProperty({"系统日志", "操作人员"})
    private String actorName;

    @ApiModelProperty(value = "日志类型  system :系统；  operation:操作")
    private String type;

    @ApiModelProperty(value = "操作类型, other : 其它; add : 新增 ; update : 修改 ; delete : 删除; query : 查询; login : 用户登录; logout: 用户登出")
    @ExcelProperty({"系统日志", "操作类型"})
    private String actType;

    @ApiModelProperty(value = "操作模块")
    private String actModule;

    @ApiModelProperty(value = "操作功能")
    @ExcelProperty({"系统日志", "操作功能"})
    private String actAction;

    @ApiModelProperty(value = "操作结果")
    private String actResult;

    @ApiModelProperty(value = "操作对象")
    @ExcelProperty({"系统日志", "操作对象"})
    private String actObj;

    @ApiModelProperty(value = "操作信息")
    private String actMessage;

    @ApiModelProperty(value = "操作ip信息")
    @ExcelProperty({"系统日志", "客户端地址"})
    private String clientIp;

    @ApiModelProperty(value = "操作时间")
    @ExcelProperty({"系统日志", "时间"})
    private LocalDateTime createTime;

    @ApiModelProperty(value = "日志埋点id")
    private String pointId;

    @TableField(exist = false)
    private LocalDate time;

}
