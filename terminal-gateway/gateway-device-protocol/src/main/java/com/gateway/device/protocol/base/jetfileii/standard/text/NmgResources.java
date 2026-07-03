package com.gateway.device.protocol.base.jetfileii.standard.text;

/**
 * Nmg TEXT FILE 协议通用工具方法。
 */
public final class NmgResources {

    private NmgResources() {
    }

    /**
     * 构建自定义 BGR 颜色字节序列 ( '/' B G R )，各通道自动钳位到 0–255。
     */
    public static byte[] customBgr(int r, int g, int b) {
        return new byte[]{
                '/',
                (byte) Math.min(255, Math.max(0, b)),
                (byte) Math.min(255, Math.max(0, g)),
                (byte) Math.min(255, Math.max(0, r))
        };
    }

    /**
     * §15.1 行间距 (0x08): 0–9 像素
     */
    public static char lineSpaceCode(int px) {
        return (char) ('0' + Math.min(9, Math.max(0, px)));
    }
}
