package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.12 字体及大小 (0x1A)——C:\FONT\ 下字库文件。
 */
@Getter
@AllArgsConstructor
public enum JetFileIIFont {
    EN_5x5('0', "5×5 英文", "Normal5.fnt"),
    EN_7x6('1', "7×6 英文", "Normal7.fnt"),
    EN_14x8('2', "14×8 英文", "Normal14.fnt"),
    EN_15x9('3', "15×9 英文", "Normal15.fnt"),
    EN_16x9('4', "16×9 英文", "Normal16.fnt"),
    EN_24x16('6', "24×16 英文", "Normal24.fnt"),
    EN_32x18('8', "32×18 英文", "Normal32.fnt"),
    EN_11x9(':', "11×9 英文", "Normal11.fnt"),
    EN_12x7(';', "12×7 英文"),
    EN_22x18('<', "22×18 英文", "Normal22.fnt"),
    EN_30x18('=', "30×18 英文", "Normal30.fnt"),
    EN_40x21('>', "40×21 英文", "Normal40.fnt"),
    CN_16x16('5', "16×16 中文", "SonTi16.FNT"),
    CN_24x24('7', "24×24 中文", "SonTi24.FNT"),
    CN_32x32('9', "32×32 中文", "SonTi32.FNT"),
    TC_16x16('?', "16×16 繁体简码", "BIG5A_16.fnt"),
    AD_7x6('@', "7×6 自适应"),
    AD_14x8('A', "14×8 自适应"),
    AD_15x8('B', "15×8 自适应"),
    AD_16x8('C', "16×8 自适应"),
    AD_24x12('D', "24×12 自适应"),
    AD_32x16('E', "32×16 自适应"),
    AD_40x21('F', "40×21 自适应"),
    SONG_40('G', "40号宋体", "SonTi40.FNT"),
    HEI_40('H', "40号黑体", "HeiTi40.FNT"),
    XINWEI_40('I', "40号新魏", "XiWei40.FNT"),
    XINGKAI_40('J', "40号行楷", "XinKai40.FNT"),
    LISHU_40('K', "40号隶书", "LiShu40.FNT"),
    YOUYUAN_40('L', "40号幼圆", "YuYuan40.FNT"),
    BOLD_14x10('N', "14×10 粗体", "bold14.fnt"),
    BOLD_15x10('O', "15×10 粗体", "bold15.fnt"),
    BOLD_16x12('P', "16×12 粗体", "bold16.fnt"),
    BOLD_24x8('Q', "24×8 粗体"),
    BOLD_32x8('R', "32×8 粗体", "bold32.fnt"),
    BOLD_11x7('S', "11×7 粗体", "bold11.fnt"),
    BOLD_12x7('T', "12×7 粗体"),
    BOLD_22x12('U', "22×12 粗体", "bold22.fnt"),
    BOLD_40x21('V', "40×21 粗体", "bold40.fnt"),
    HEI_24('W', "24号黑体", "HeiTi24.FNT"),
    XINWEI_24('X', "24号新魏", "XiWei24.FNT"),
    XINGKAI_24('Y', "24号行楷", "XinKai24.FNT"),
    LISHU_24('Z', "24号隶书", "LiShu24.FNT"),
    YOUYUAN_24('[', "24号幼圆", "YuYuan24.FNT"),
    HEI_32('\\', "32号黑体", "HeiTi32.FNT"),
    XINWEI_32(']', "32号新魏", "XiWei32.FNT"),
    XINGKAI_32('^', "32号行楷", "XinKai32.FNT"),
    LISHU_32('_', "32号隶书", "LiShu32.FNT"),
    YOUYUAN_32('`', "32号幼圆", "YuYuan32.FNT"),
    USER_1('a', "用户自定义1"),
    USER_2('b', "用户自定义2"),
    USER_3('c', "用户自定义3", "RUSSIA~1.FNT"),
    USER_4('d', "用户自定义4"),
    USER_5('e', "用户自定义5"),
    USER_6('f', "用户自定义6"),
    USER_7('g', "用户自定义7"),
    USER_8('h', "用户自定义8"),
    USER_9('i', "用户自定义9"),
    ;

    private final char code;
    private final String label;
    /**
     * C:\FONT\ 下的字库文件名，无独立文件返回 null
     */
    private final String devicePath;

    JetFileIIFont(char code, String label) {
        this(code, label, null);
    }

    public static JetFileIIFont ofCode(char code) {
        for (JetFileIIFont v : values()) if (v.code == code) return v;
        return null;
    }
}
