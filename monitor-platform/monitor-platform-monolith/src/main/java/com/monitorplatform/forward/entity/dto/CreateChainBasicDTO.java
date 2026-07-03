package com.monitorplatform.forward.entity.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class CreateChainBasicDTO implements Serializable {


    private static final long serialVersionUID = 1L;

    @NotBlank(message = "链路名称不能为空")
    private String chainName;

    @NotBlank(message = "链路编号不能为空")
    private String chainCode;


    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled = 1;

    private String createdBy;

    /**
     * 链路描述
     */
    private String remark;
}
