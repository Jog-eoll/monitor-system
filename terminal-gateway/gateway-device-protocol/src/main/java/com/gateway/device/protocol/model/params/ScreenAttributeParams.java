package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * LED 显示屏点阵配屏参数 —— SCREEN_ATTRIBUTE_SET。
 *
 * <p>SDK: {@code nvSetScreenAttributeAsync}
 * <br>设置 LED 模组物理点阵（接收卡带载宽高），区别于视频输出分辨率设置。
 * <br>JSON 结构: {@code {"sn":"...","screenAttribute":{"screenAttributes":[{...}]}}}</p>
 */
@Data
@Builder
public class ScreenAttributeParams implements CommandParams {

    /**
     * 显示屏 id，默认 0
     */
    @Builder.Default
    private int id = 0;

    /**
     * 配屏参数来源，1=LCT 配屏
     */
    @Builder.Default
    private int screenSource = 1;

    /**
     * X 方向接收卡个数
     */
    @Builder.Default
    private int xCount = 1;

    /**
     * Y 方向接收卡个数
     */
    @Builder.Default
    private int yCount = 1;

    /**
     * X 方向显示偏移
     */
    @Builder.Default
    private int xOffset = 0;

    /**
     * Y 方向显示偏移
     */
    @Builder.Default
    private int yOffset = 0;

    /**
     * 网口数量
     */
    @Builder.Default
    private int portNumber = 1;

    /**
     * 走线顺序，默认 [0, 1]
     */
    @Builder.Default
    private List<Integer> orders = Arrays.asList(0, 1);

    /**
     * 接收卡带载宽度（LED 点阵宽度，如 64、96、128）
     */
    private Integer width;

    /**
     * 接收卡带载高度（LED 点阵高度，如 64、96、128）
     */
    private Integer height;
}
