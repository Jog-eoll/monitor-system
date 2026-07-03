package com.gateway.device.protocol.base.jetfileii.standard.checksum;

/**
 * 校验和计算接口。
 */
public interface Checksum {

    /**
     * 计算校验和。
     *
     * @param data   数据
     * @param offset 起始偏移
     * @param length 计算长度
     * @return 校验和值
     */
    int compute(byte[] data, int offset, int length);
}
