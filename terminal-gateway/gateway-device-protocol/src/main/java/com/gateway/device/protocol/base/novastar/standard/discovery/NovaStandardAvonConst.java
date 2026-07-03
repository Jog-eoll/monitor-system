package com.gateway.device.protocol.base.novastar.standard.discovery;

import java.nio.charset.StandardCharsets;

/**
 * AVON 设备发现协议常量。
 *
 * <p>AVON 是 NovaStandard 设备 UDP 广播发现的私有协议，与标准帧协议 (0xAA/0xCC) 独立。</p>
 */
public final class NovaStandardAvonConst {

    /**
     * 协议魔数 "AVON"
     */
    public static final byte[] AVON_MAGIC = "AVON".getBytes(StandardCharsets.US_ASCII);

    /**
     * 协议标识 0x8855 (LE)
     */
    public static final int AVON_PROTO_ID = 0x8855;

    /**
     * 广播标识
     */
    public static final long BROADCAST_FLAG = 0xFFFFFFFFL;

    /**
     * 搜索指令
     */
    public static final int CMD_SEARCH = 0x0081;

    /**
     * 搜索回复
     */
    public static final int CMD_SEARCH_REPLY = 0x0001;

    /**
     * 包尾标识
     */
    public static final int TRAILER = 0x008F;

    /**
     * 请求包固定长度
     */
    public static final int REQUEST_LEN = 24;

    /**
     * 回复包头部最小长度（不含 JSON 载荷）
     */
    public static final int REPLY_HEADER_LEN = 24;

    private NovaStandardAvonConst() {
    }
}
