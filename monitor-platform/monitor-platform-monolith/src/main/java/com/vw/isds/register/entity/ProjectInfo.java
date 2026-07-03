package com.vw.isds.register.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 项目信息
 * </p>
 *
 * @author suweiming
 * @since 2022-07-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "ProjectInfo对象", description = "项目信息")
public class ProjectInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @ApiModelProperty(value = "唯一编码")
    private String indexCode;

    @ApiModelProperty(value = "项目名称")
    private String name;

    @ApiModelProperty(value = "公司地址")
    private String address;

    @ApiModelProperty(value = "建筑面积")
    private String buildArea;

    @ApiModelProperty(value = "产权面积")
    private String propertyArea;

    @ApiModelProperty(value = "开业日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime openingDate;

    @ApiModelProperty(value = "图片服务唯一标识码")
    @TableField("aswSyscode")
    private String aswSyscode;

    @ApiModelProperty(value = "公钥")
    private String publicKey;

    @ApiModelProperty(value = "授权文件")
    private String key1;

    @ApiModelProperty(value = "项目图片")
    private String picUrl;

    @ApiModelProperty(value = "扩展信息")
    private String extension;

    @ApiModelProperty(value = "逻辑删除标识 0正常  1删除")
    private String deleteFlag;

}
