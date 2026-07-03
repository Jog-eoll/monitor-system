package com.gateway.device.protocol.base.colorlight.standard;

/**
 * ColorLight LAN Player SDK 协议常量定义。
 */
public final class ColorLightCommand {

    // ═══════════════════════════════════════════════════════════════
    // Region Name — 区域类型名称
    // ═══════════════════════════════════════════════════════════════
    /**
     * 同步节目（item 为 2,3,6）
     */
    public static final String REGION_SYNC_PROGRAM = "sync_program";
    /**
     * 单行滚动节目
     */
    public static final String REGION_SINGLELINE_SCROLL = "singleline_scroll";

    // ═══════════════════════════════════════════════════════════════
    // DateStyle — 时间日期风格
    // ═══════════════════════════════════════════════════════════════
    public static final int DATE_STYLE_YYYY_MM_DD = 1;
    public static final int DATE_STYLE_MM_DD_YYYY = 2;
    public static final int DATE_STYLE_M_DD_YYYY = 3;
    public static final int DATE_STYLE_YYYY_MM_DD_SPACE = 4;
    public static final int DATE_STYLE_YYYY_M_DD = 5;
    public static final int DATE_STYLE_MONTH_DD_YYYY = 6;
    public static final int DATE_STYLE_DD_MM_YYYY = 7;
    public static final int DATE_STYLE_DD_MM_YYYY_DOT = 8;
    public static final int DATE_STYLE_DD_M_YYYY = 9;
    public static final int DATE_STYLE_DD_MON_YYYY = 10;
    public static final int DATE_STYLE_DD_MON_YYYY_DOT = 11;
    public static final int DATE_STYLE_DD_MON_YYYY_DASH = 12;
    public static final int DATE_STYLE_CN = 13;

    // ═══════════════════════════════════════════════════════════════
    // Clock Flags — 时钟显示标志（按位或组合）
    // ═══════════════════════════════════════════════════════════════
    public static final int CLOCK_FLAG_FIXED_TEXT = 0;
    public static final int CLOCK_FLAG_YEAR = 1;
    public static final int CLOCK_FLAG_MONTH = 2;
    public static final int CLOCK_FLAG_DAY = 4;
    public static final int CLOCK_FLAG_HOUR = 8;
    public static final int CLOCK_FLAG_MINUTE = 16;
    public static final int CLOCK_FLAG_SECOND = 32;
    public static final int CLOCK_FLAG_WEEK = 512;
    public static final int CLOCK_FLAG_AMPM = 1024;
    public static final int CLOCK_FLAG_H24 = 2048;
    public static final int CLOCK_FLAG_YEAR_2DIGIT = 4096;
    public static final int CLOCK_FLAG_MULTILINE = 8192;

    // ═══════════════════════════════════════════════════════════════
    // HhourScale Shape — 时标形状
    // ═══════════════════════════════════════════════════════════════
    public static final int SCALE_SHAPE_CIRCLE = 0;
    public static final int SCALE_SHAPE_SQUARE = 1;
    public static final int SCALE_SHAPE_DIGIT = 2;

    // ═══════════════════════════════════════════════════════════════
    // IsScroll — 文本是否滚动
    // ═══════════════════════════════════════════════════════════════
    public static final int SCROLL_NO = 0;
    public static final int SCROLL_YES = 1;

    // ═══════════════════════════════════════════════════════════════
    // EffectType — 入场特效
    // ═══════════════════════════════════════════════════════════════
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_RANDOM = 1;
    public static final int EFFECT_COVER_LEFT = 2;
    public static final int EFFECT_COVER_RIGHT = 3;
    public static final int EFFECT_COVER_TOP = 4;
    public static final int EFFECT_COVER_BOTTOM = 5;
    public static final int EFFECT_COVER_TL_DIAG = 6;
    public static final int EFFECT_COVER_TR_DIAG = 7;
    public static final int EFFECT_COVER_BL_DIAG = 8;
    public static final int EFFECT_COVER_BR_DIAG = 9;
    public static final int EFFECT_COVER_TL_LINE = 10;
    public static final int EFFECT_COVER_TR_LINE = 11;
    public static final int EFFECT_COVER_BL_LINE = 12;
    public static final int EFFECT_COVER_BR_LINE = 13;
    public static final int EFFECT_BLINDS_H = 14;
    public static final int EFFECT_BLINDS_V = 15;
    public static final int EFFECT_OPEN_LR = 16;
    public static final int EFFECT_OPEN_TB = 17;
    public static final int EFFECT_CLOSE_LR = 18;
    public static final int EFFECT_CLOSE_TB = 19;
    public static final int EFFECT_MOVE_UP = 20;
    public static final int EFFECT_MOVE_DOWN = 21;
    public static final int EFFECT_MOVE_LEFT = 22;
    public static final int EFFECT_MOVE_RIGHT = 23;
    public static final int EFFECT_MOVE_TL = 24;
    public static final int EFFECT_MOVE_TR = 25;
    public static final int EFFECT_MOVE_BL = 26;
    public static final int EFFECT_MOVE_BR = 27;
    public static final int EFFECT_MOSAIC_SMALL = 28;
    public static final int EFFECT_MOSAIC_MEDIUM = 29;
    public static final int EFFECT_MOSAIC_LARGE = 30;
    public static final int EFFECT_FADE = 31;
    public static final int EFFECT_ROTATE_R_360 = 32;
    public static final int EFFECT_ROTATE_L_360 = 33;
    public static final int EFFECT_ROTATE_R_180 = 34;
    public static final int EFFECT_ROTATE_L_180 = 35;
    public static final int EFFECT_ROTATE_R_90 = 36;
    public static final int EFFECT_ROTATE_L_90 = 37;
    public static final int EFFECT_ZOOM_IN_CENTER = 38;
    public static final int EFFECT_ZOOM_IN_TL = 39;
    public static final int EFFECT_ZOOM_IN_TR = 40;
    public static final int EFFECT_ZOOM_IN_BR = 41;
    public static final int EFFECT_ZOOM_IN_BL = 42;
    public static final int EFFECT_RECT_OUTWARD = 43;
    public static final int EFFECT_RECT_INWARD = 44;
    public static final int EFFECT_DIAMOND_OUTWARD = 45;
    public static final int EFFECT_DIAMOND_INWARD = 46;
    public static final int EFFECT_CROSS_OUTWARD = 47;
    public static final int EFFECT_CROSS_INWARD = 48;
    public static final int EFFECT_3D_1 = 49;
    public static final int EFFECT_3D_2 = 50;

    // ═══════════════════════════════════════════════════════════════
    // GradientMode
    // ═══════════════════════════════════════════════════════════════
    public static final int GRADIENT_CLAMP = 0;
    public static final int GRADIENT_REPEAT = 1;
    public static final int GRADIENT_MIRROR = 2;

    // ═══════════════════════════════════════════════════════════════
    // TextPosition
    // ═══════════════════════════════════════════════════════════════
    public static final int POSITION_TOP = 0;
    public static final int POSITION_BOTTOM = 1;
    public static final int POSITION_LEFT = 2;
    public static final int POSITION_RIGHT = 3;

    private ColorLightCommand() {
    }
}
