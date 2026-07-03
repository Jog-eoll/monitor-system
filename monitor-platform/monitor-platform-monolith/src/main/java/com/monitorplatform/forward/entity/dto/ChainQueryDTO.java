package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 链路查询条件DTO
 */
@Data
public class ChainQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 链路名称（模糊查询）
     */
    private String chainName;

    /**
     * 链路编码
     */
    private String chainCode;

    /**
     * 链路状态: 0-离线/不可用, 1-在线/可用, 2-部分可用
     */
    private Integer status;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 设备ID（查询包含该设备的链路）
     */
    private String deviceId;

    /**
     * 分支编码（查询指定分支的链路）
     */
    private String branchCode;

    /**
     * 当前页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;
}
