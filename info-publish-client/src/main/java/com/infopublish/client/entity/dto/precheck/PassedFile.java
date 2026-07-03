package com.infopublish.client.entity.dto.precheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 校验通过的内部文件信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassedFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内部文件 ID */
    private String innerFileId;

    /** 内部文件名 */
    private String fileName;
}
