package com.infopublish.client.entity.dto.precheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 校验失败的内部文件信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内部文件 ID */
    private String innerFileId;

    /** 内部文件名 */
    private String fileName;

    /** 失败阶段 */
    private String stage;

    /** 错误码 */
    private String code;

    /** 错误说明 */
    private String message;
}
