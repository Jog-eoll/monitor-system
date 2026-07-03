package com.gateway.device.protocol.base.jetfileii.standard.command;

/**
 * JetFileII 状态码常量。
 */
public final class StatusCode {

    /**
     * 成功
     */
    public static final short OK = (short) 0x9000;
    // 通信错误 (9xxx)
    public static final short ERR_CHECKSUM = (short) 0x9002;
    public static final short ERR_ADDRESS = (short) 0x9003;
    public static final short ERR_MAIN_CMD = (short) 0x9004;
    public static final short ERR_SUB_CMD = (short) 0x9005;
    public static final short ERR_PACK_LEN = (short) 0x9006;
    public static final short ERR_FILE_NOT_EXIST = (short) 0x9008;
    public static final short ERR_FILE_EOF = (short) 0x9009;
    public static final short ERR_FILE_OPEN = (short) 0x9010;
    public static final short ERR_NOT_SUPPORTED = (short) 0x9011;
    public static final short ERR_DISK_FULL = (short) 0x9012;
    public static final short ERR_PACK_TOO_LARGE = (short) 0x9013;
    public static final short ERR_PACK_ORDER = (short) 0x9014;
    public static final short ERR_FILE_BUSY = (short) 0x9015;
    // 绝对地址读取错误 (1xxx)
    public static final short ERR_READ_TOO_LARGE = (short) 0x1101;
    public static final short ERR_DISK_IO = (short) 0x1102;
    // CPU 升级错误 (1Fxx)
    public static final short ERR_UPGRADE_FAIL = (short) 0x1F01;
    public static final short ERR_UPGRADE_RUNNING = (short) 0x1F02;
    public static final short ERR_UPGRADE_NONE = (short) 0x1F03;
    // 写入错误 (2xxx)
    public static final short ERR_MEM_ALLOC = (short) 0x2000;
    public static final short ERR_FILE_TOO_LARGE = (short) 0x2101;
    public static final short ERR_DISK_C_FULL = (short) 0x2103;
    public static final short ERR_DISK_D_FULL = (short) 0x2104;
    public static final short ERR_DISK_E_FULL = (short) 0x2105;
    public static final short ERR_DISK_F_FULL = (short) 0x2106;
    public static final short ERR_EMERGENCY_LARGE = (short) 0x2901;
    // 灰度测试 (3Axx)
    public static final short ERR_GRAY_NOT_SUPPORT = (short) 0x3A01;
    // 时间错误 (5xxx)
    public static final short ERR_TIME_SET = (short) 0x5201;
    // 显示/播放错误 (6xxx)
    public static final short ERR_NO_DISP_FILE = (short) 0x6701;
    public static final short ERR_DISP_FILE_OPEN = (short) 0x6702;
    public static final short ERR_DISP_FILE_LARGE = (short) 0x6703;
    // 文件操作错误 (7xxx)
    public static final short ERR_FORMAT_FAIL = (short) 0x7201;
    public static final short ERR_MKDIR_FAIL = (short) 0x7301;
    public static final short ERR_RENAME_FAIL = (short) 0x7401;
    public static final short ERR_RENAME_PATH = (short) 0x7402;
    public static final short ERR_MOVE_FAIL = (short) 0x7501;
    public static final short ERR_DELETE_FAIL = (short) 0x7601;
    public static final short ERR_DIR_OPEN_FAIL = (short) 0x7B01;
    public static final short ERR_DISK_INFO_FAIL = (short) 0x7D01;
    public static final short ERR_FILE_NOT_FOUND = (short) 0x7E01;
    // 无限连接播放状态码 (83xx)
    public static final short STAT_INF_PLAY_12 = (short) 0x8301;
    public static final short STAT_INF_PLAY_23 = (short) 0x8302;
    public static final short STAT_INF_PLAY_31 = (short) 0x8303;
    public static final short STAT_INF_NOT_PLAY = (short) 0x8305;
    public static final short STAT_INF_OVERFLOW = (short) 0x8306;
    public static final short STAT_INF_FORMAT = (short) 0x8307;
    // 登录错误 (90xx)
    public static final short ERR_NOT_LOGIN = (short) 0x9030;
    public static final short ERR_WRONG_PWD = (short) 0x9031;
    public static final short ERR_WRONG_USER = (short) 0x9032;
    public static final short ERR_OLD_PWD_WRONG = (short) 0x9033;
    public static final short ERR_OTHER_LOGIN = (short) 0x9035;
    // FTP/HTTP 状态码 (B1xx)
    public static final short STAT_FTP_OPEN = (short) 0xB101;
    public static final short STAT_HTTP_OPEN = (short) 0xB102;

    private StatusCode() {
    }

    public static boolean isOk(short statusCode) {
        return statusCode == OK;
    }
}
