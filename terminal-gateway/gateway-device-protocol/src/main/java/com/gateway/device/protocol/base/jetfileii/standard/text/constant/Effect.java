package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * NMG §15.2 出入花样 (0x0A)。
 */
@Getter
@AllArgsConstructor
public enum Effect {
    RANDOM((byte) 0x2F, "随机"),
    JUMP_OUT((byte) 0x30, "跳出"),
    MOVE_LEFT((byte) 0x31, "左移动"),
    MOVE_RIGHT((byte) 0x32, "右移动"),
    PULL_LEFT((byte) 0x33, "左拉动"),
    PULL_RIGHT((byte) 0x34, "右拉动"),
    MOVE_UP((byte) 0x35, "上移动"),
    MOVE_DOWN((byte) 0x36, "下移动"),
    SPLIT_MID((byte) 0x37, "中间拉开"),
    PULL_UP((byte) 0x38, "上拉动"),
    PULL_DOWN((byte) 0x39, "下拉动"),
    CLOSE_MID((byte) 0x3A, "中间拉合"),
    CLOSE_TB((byte) 0x3B, "上下拉合"),
    OPEN_TB((byte) 0x3C, "上下拉开"),
    WEAVE_LR((byte) 0x3D, "左右穿插"),
    WEAVE_TB((byte) 0x3E, "上下穿插"),
    DROP_LEFT((byte) 0x3F, "左落下"),
    DROP_RIGHT((byte) 0x40, "右落下"),
    BLIND_TB((byte) 0x41, "上下百叶"),
    BLIND_LR((byte) 0x42, "左右百叶"),
    RAIN((byte) 0x43, "下雨"),
    MOSAIC((byte) 0x44, "马赛克"),
    SHAKE((byte) 0x45, "闪烁震动"),
    TWIST_OUT((byte) 0x46, "扭动出屏"),
    RADAR((byte) 0x47, "雷达"),
    FAN_OUT((byte) 0x48, "扇形展开"),
    FAN_IN((byte) 0x49, "扇形收回"),
    SPIRAL_RIGHT((byte) 0x4A, "右螺旋"),
    SPIRAL_LEFT((byte) 0x4B, "左螺旋"),
    CORNERS_OUT((byte) 0x4C, "四角拉开"),
    CORNERS_IN((byte) 0x4D, "四角拉合"),
    FOUR_DIR_OUT((byte) 0x4E, "四方拉开"),
    FOUR_DIR_IN((byte) 0x4F, "四方拉合"),
    FOUR_BLOCK_1((byte) 0x50, "四块中间1"),
    FOUR_BLOCK_2((byte) 0x51, "四块中间2"),
    FOUR_BLOCK_3((byte) 0x52, "四块中间3"),
    FOUR_BLOCK_4((byte) 0x53, "四块中间4"),
    TL_CORNER_1((byte) 0x54, "左上角拉1"),
    TR_CORNER_1((byte) 0x55, "右上角拉1"),
    BL_CORNER_1((byte) 0x56, "左下角拉1"),
    BR_CORNER_1((byte) 0x57, "右下角拉1"),
    TL_CORNER_2((byte) 0x58, "左上角拉2"),
    TR_CORNER_2((byte) 0x59, "右上角拉2"),
    BL_CORNER_2((byte) 0x5A, "左下角拉2"),
    BR_CORNER_2((byte) 0x5B, "右下角拉2"),
    MOVE_TL((byte) 0x5C, "左上角移"),
    MOVE_TR((byte) 0x5D, "右上角移"),
    MOVE_BL((byte) 0x5E, "左下角移"),
    MOVE_BR((byte) 0x5F, "右下角移"),
    GROW((byte) 0x60, "长大");

    private final byte code;
    private final String label;

    public static Effect ofCode(byte code) {
        for (Effect v : values()) if (v.code == code) return v;
        return null;
    }

    public static List<Effect> all() {
        return Collections.unmodifiableList(Arrays.asList(values()));
    }
}
