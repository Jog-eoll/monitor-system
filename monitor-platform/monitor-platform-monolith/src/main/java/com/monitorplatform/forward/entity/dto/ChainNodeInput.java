package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 链路节点输入（扁平化）
 */
@Data
public class ChainNodeInput implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 前端临时ID（用于建立连线关系，如 "node_1"）
     */
    @NotBlank(message = "临时ID不能为空")
    private String tempId;

    /**
     * 设备唯一标识
     */
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型: publish_server, publish_gateway, terminal_encrypt_gateway, content_server, info_board
     */
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    /**
     * 设备IP地址
     */
    private String deviceIp;

    /**
     * 备注信息
     */
    private String remark;
}
