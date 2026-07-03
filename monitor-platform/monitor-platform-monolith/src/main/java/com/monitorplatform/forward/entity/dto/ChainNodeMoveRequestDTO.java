package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ChainNodeMoveRequestDTO {

    @NotNull(message = "nodeId不能为空")
    private Long nodeId;

    private Long newParentId;
}
