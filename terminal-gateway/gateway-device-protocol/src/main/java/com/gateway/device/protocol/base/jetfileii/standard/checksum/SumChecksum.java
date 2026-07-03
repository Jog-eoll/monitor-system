package com.gateway.device.protocol.base.jetfileii.standard.checksum;

/**
 * 普通和校验（Byte 型累加，取低 16 位）。
 * <p>
 * 算法: 从指定偏移开始逐字节累加，结果截取低 2 字节。
 * 对应文档 21.2 节 MsgCountCheckSumTwo。
 * </p>
 */
public final class SumChecksum implements Checksum {

    public static final SumChecksum INSTANCE = new SumChecksum();

    private SumChecksum() {
    }

    /**
     * 对 data[offset .. offset+length-1] 逐字节累加，返回低 16 位。
     */
    @Override
    public int compute(byte[] data, int offset, int length) {
        int sum = 0;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            sum += (data[i] & 0xFF);
        }
        return sum & 0xFFFF;
    }

    /**
     * 便捷方法：对整个数组计算校验和。
     */
    public int compute(byte[] data) {
        return compute(data, 0, data.length);
    }
}
