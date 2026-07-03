package com.monitorplatform.common.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Description 分页公共dto
 * @Author: zqr
 * @Date: 2022/11/21 15:43:43
 */
@Data
public class PageDTO {

    /**
     * 当前页
     */
    @ApiModelProperty(value = "当前页", position = 1)
    @JsonAlias("currentPage")
    private Long current;

    /**
     * 每页显示数量
     */
    @ApiModelProperty(value = "每页显示数量", position = 2)
    @JsonAlias("pageSize")
    private Long size;

    @ApiModelProperty(hidden = true)
    private boolean flag;


}
