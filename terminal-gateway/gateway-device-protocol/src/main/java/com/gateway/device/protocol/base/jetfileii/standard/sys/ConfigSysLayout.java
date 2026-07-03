package com.gateway.device.protocol.base.jetfileii.standard.sys;

/**
 * CONFIG.SYS 二进制文件字段布局 —— 偏移常量和 IPv4 小端编码工具。
 *
 * <p>JetFileII 设备的 CONFIG.SYS 为固定结构二进制文件。
 * 通过 {@link com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer#readSysFile}
 * 读取整个文件后，按此布局修改字段，再写回设备。</p>
 *
 * <p>所有 IP 字段以 4 字节 Little-Endian uint32 存储。</p>
 */
public final class ConfigSysLayout {

    /**
     * 系统文件名
     */
    public static final String FILE_NAME = SysFileName.CONFIG_SYS;

    // ── 字段偏移 ──
    /**
     * DHCP 模式标志偏移（0x00=静态 IP, 0x01=DHCP 启用）
     */
    public static final int OFF_DHCP = 0x0E;
    /**
     * IP 地址偏移
     */
    public static final int OFF_IP = 0x24;
    /**
     * 网关偏移
     */
    public static final int OFF_GATEWAY = 0x88;
    /**
     * 子网掩码偏移
     */
    public static final int OFF_NETMASK = 0x8C;
    /**
     * DNS 偏移（CONFIG.SYS 尾部 8.8.8.8 位置）。
     * <p>帧偏移 0x14C - header 16B - arg 8B = 数据偏移 0x134</p>
     */
    public static final int OFF_DNS = 0x134;

    /**
     * DHCP 启用（设备自动获取 IP）
     */
    public static final byte DHCP_ENABLE = 0x01;
    /**
     * 静态 IP 模式
     */
    public static final byte DHCP_STATIC = 0x00;

    /**
     * IPv4 字段长度（字节）
     */
    public static final int IPV4_SIZE = 4;

    /**
     * CONFIG.SYS 有效最小长度（至少能容纳最后一个字段）
     */
    public static final int MIN_SIZE = OFF_DNS + IPV4_SIZE;

    private ConfigSysLayout() {
    }

    /**
     * 读取 DHCP 标志：{@code true}=DHCP 启用，{@code false}=静态 IP。
     */
    public static boolean isDhcp(byte[] data) {
        return data[OFF_DHCP] == DHCP_ENABLE;
    }

    /**
     * 写入 DHCP 标志字节。
     *
     * @param data CONFIG.SYS 数据
     * @param dhcp true=DHCP 启用, false=静态 IP
     */
    public static void writeDhcpFlag(byte[] data, boolean dhcp) {
        data[OFF_DHCP] = dhcp ? DHCP_ENABLE : DHCP_STATIC;
    }

    /**
     * IPv4 字符串转为 4 字节小端字节数组（低位在前）。
     * <p>例: "192.168.113.61" → [0x3d, 0x71, 0xa8, 0xc0]</p>
     * <p>LE uint32 = 0xc0a8713d，byte[0]=LSB=0x3d=61 ... byte[3]=MSB=0xc0=192</p>
     */
    public static byte[] ipToBytesLE(String ip) {
        String[] parts = ip.split("\\.");
        byte[] b = new byte[IPV4_SIZE];
        for (int i = 0; i < IPV4_SIZE; i++) {
            b[IPV4_SIZE - 1 - i] = (byte) (Integer.parseInt(parts[i]) & 0xFF);
        }
        return b;
    }

    /**
     * 从 byte[] 指定偏移读取 4 字节小端并解析为 IPv4 字符串。
     * <p>byte[off]=LSB ... byte[off+3]=MSB，显示时高位在前</p>
     */
    public static String bytesToIpLE(byte[] data, int off) {
        return (data[off + 3] & 0xFF) + "." + (data[off + 2] & 0xFF) + "."
                + (data[off + 1] & 0xFF) + "." + (data[off] & 0xFF);
    }

    /**
     * 将 IPv4 字符串写入 byte[] 指定偏移（4 字节 LE），不检查格式。
     *
     * @param data 目标字节数组
     * @param off  写入偏移
     * @param ip   IPv4 字符串，格式须合法
     */
    public static void writeIpLE(byte[] data, int off, String ip) {
        byte[] b = ipToBytesLE(ip);
        System.arraycopy(b, 0, data, off, IPV4_SIZE);
    }
}
