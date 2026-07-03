package com.gateway.device.protocol.base.jetfileii.standard.command;

/**
 * JetFileII 大类命令。
 */
public final class MainCmd {

    public static final byte READ = 0x01;
    public static final byte WRITE = 0x02;
    public static final byte TEST = 0x03;
    public static final byte CONTROL = 0x04;
    public static final byte TIME = 0x05;
    public static final byte DISPLAY = 0x06;
    public static final byte FILE_CTL = 0x07;
    public static final byte INFINITE_PLAY = 0x08;
    public static final byte CHAIN_PLAY = 0x09;
    public static final byte LOGIN = 0x0A;
    public static final byte DOT_DETECT = 0x10;
    public static final byte FTP_HTTP = 0x11;
    public static final byte WARNING = 0x31;
    public static final byte VPU3400 = 0x34;
    public static final byte MULTI_AREA = 0x36;
    public static final byte XG_ARROW = 0x37;
    public static final byte LOG_WRITE = 0x7E;

    private MainCmd() {
    }

    public static String nameOf(byte mainCmd) {
        switch (mainCmd) {
            case READ:
                return "信息读取";
            case WRITE:
                return "信息写入";
            case TEST:
                return "测试指令";
            case CONTROL:
                return "系统操作";
            case TIME:
                return "时间指令";
            case DISPLAY:
                return "播放控制";
            case FILE_CTL:
                return "文件控制";
            case INFINITE_PLAY:
                return "无限连接播放";
            case CHAIN_PLAY:
                return "接龙播放";
            case LOGIN:
                return "登录操作";
            case DOT_DETECT:
                return "像点检测";
            case FTP_HTTP:
                return "FTP/HTTP开关";
            case WARNING:
                return "警告上报";
            case VPU3400:
                return "VPU3400";
            case MULTI_AREA:
                return "多区域操作";
            case XG_ARROW:
                return "红叉绿箭";
            case LOG_WRITE:
                return "日志写入";
            default:
                return "未知(0x" + Integer.toHexString(mainCmd & 0xFF) + ")";
        }
    }
}
