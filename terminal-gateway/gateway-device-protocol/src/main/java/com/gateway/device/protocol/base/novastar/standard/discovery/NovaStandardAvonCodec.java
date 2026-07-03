package com.gateway.device.protocol.base.novastar.standard.discovery;

import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AVON 发现协议编解码。
 *
 * <pre>
 *   请求: 固定 24 字节，格式: AVON + 0xFFFFFFFF + 0x8855 + 0x0081 + ...
 *   回复: 变长，头部 24 字节 + JSON 载荷
 * </pre>
 */
public final class NovaStandardAvonCodec {

    /**
     * 构建 24 字节 AVON 搜索请求。
     */
    public byte[] buildSearchRequest() {
        byte[] buf = new byte[NovaStandardAvonConst.REQUEST_LEN];

        // [0-3] "AVON" 魔数
        System.arraycopy(NovaStandardAvonConst.AVON_MAGIC, 0, buf, 0, 4);

        // [4-7] 广播标识 0xFFFFFFFF
        LittleEndianByteBufUtils.writeUIntLE(buf, 4, NovaStandardAvonConst.BROADCAST_FLAG);

        // [8-9] 协议标识 0x8855
        LittleEndianByteBufUtils.writeUShortLE(buf, 8, NovaStandardAvonConst.AVON_PROTO_ID);

        // [10-11] 搜索指令 0x0081
        LittleEndianByteBufUtils.writeUShortLE(buf, 10, NovaStandardAvonConst.CMD_SEARCH);

        // [12-15] 参数值 1
        LittleEndianByteBufUtils.writeUIntLE(buf, 12, 1);

        // [16-19] 保留
        LittleEndianByteBufUtils.writeUIntLE(buf, 16, 0);

        // [20-21] 保留
        LittleEndianByteBufUtils.writeUShortLE(buf, 20, 0);

        // [22-23] 尾部标识 0x008F
        LittleEndianByteBufUtils.writeUShortLE(buf, 22, NovaStandardAvonConst.TRAILER);

        return buf;
    }

    /**
     * 解析 AVON 回复包。
     *
     * @param raw        原始字节
     * @param sourceIp   来源 IP
     * @param sourcePort 来源端口
     * @return 解析后的回复对象，解析失败返回 null
     */
    public NovaStandardAvonReply parseReply(byte[] raw, String sourceIp, int sourcePort) {
        if (raw == null || raw.length < NovaStandardAvonConst.REPLY_HEADER_LEN) {
            return null;
        }

        // 校验魔数
        if (raw[0] != NovaStandardAvonConst.AVON_MAGIC[0]
                || raw[1] != NovaStandardAvonConst.AVON_MAGIC[1]
                || raw[2] != NovaStandardAvonConst.AVON_MAGIC[2]
                || raw[3] != NovaStandardAvonConst.AVON_MAGIC[3]) {
            return null;
        }

        int cmd = LittleEndianByteBufUtils.readUShortLE(raw, 10);
        if (cmd != NovaStandardAvonConst.CMD_SEARCH_REPLY) {
            return null;
        }

        // 载荷长度
        int payloadLen = LittleEndianByteBufUtils.readUShortLE(raw, 16);
        int jsonStart = NovaStandardAvonConst.REPLY_HEADER_LEN;
        int jsonEnd = Math.min(jsonStart + payloadLen, raw.length);
        String json = new String(raw, jsonStart, jsonEnd - jsonStart, StandardCharsets.UTF_8);

        try {
            NovaStandardAvonReply reply = JsonCustomMapper.get().readValue(json, NovaStandardAvonReply.class);
            reply.setIp(sourceIp);
            reply.setSourcePort(sourcePort);
            return reply;
        } catch (IOException e) {
            return null;
        }
    }
}
