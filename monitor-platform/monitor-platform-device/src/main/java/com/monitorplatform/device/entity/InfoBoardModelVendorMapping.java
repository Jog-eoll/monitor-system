package com.monitorplatform.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 情报板型号-厂商映射实体
 * <p>
 * 用于将前端选择的情报板型号（如A2K/A4K）映射到平台侧厂商编码和解密网关侧厂商提示。
 * </p>
 */
@Data
@TableName("info_board_model_vendor_mapping")
public class InfoBoardModelVendorMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 型号编码（如A2K/A4K） */
    private String modelCode;

    /** 型号名称 */
    private String modelName;

    /** 平台侧厂商编码（如colorlight） */
    private String platformManufacturer;

    /** 解密网关侧厂商提示（如COLORLIGHT） */
    private String terminalVendorHint;

    /** 默认端口号 */
    private Integer defaultPort;

    /** 是否启用（1-启用/0-禁用） */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
