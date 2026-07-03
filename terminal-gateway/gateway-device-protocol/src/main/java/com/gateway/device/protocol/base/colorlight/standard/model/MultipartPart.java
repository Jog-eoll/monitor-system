package com.gateway.device.protocol.base.colorlight.standard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * multipart/form-data 表单部分。
 *
 * <p>每个部分对应一个表单字段，包含文件名、MIME 类型和二进制内容。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartPart {
    /**
     * 表单字段名，如 "f1" "f2"
     */
    private String name;
    /**
     * 文件名（Content-Disposition filename），可为空
     */
    private String fileName;
    /**
     * 文件二进制内容
     */
    private byte[] data;
}
