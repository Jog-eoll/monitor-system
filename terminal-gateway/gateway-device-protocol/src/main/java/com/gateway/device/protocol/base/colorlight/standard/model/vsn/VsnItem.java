package com.gateway.device.protocol.base.colorlight.standard.model.vsn;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.*;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.clock.*;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.ItemType;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.ReserveMode;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.LogFont;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.TextGradient;
import com.gateway.device.protocol.common.serialize.BigDecimalWithScaleSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 节目素材项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VsnItem {

    // ══════ 通用 ══════
    private ItemType type;
    private String backColor;
    @JsonProperty("inEffect")
    private Effect inEffect;
    private String name;
    private Schedule schedule;

    // ══════ 图片/视频/GIF/文档 共用 ══════
    private FileSource fileSource;
    /**
     * FIXME[类型存疑] 数字|浮点数字
     */
    private Float alpha;
    /**
     * 音量，范围 0.0~1.0，默认 1.0。
     * 图片和视频素材均含此字段。
     */
    private Float volume;
    private Long duration;
    private ReserveMode reserveAS;

    // ══════ GIF ══════
    private Integer playTimes;

    // ══════ 文本 (Type=4/5) ══════
    private String text;
    private Integer isScroll;
    private LogFont logFont;
    @JsonProperty("ftColor")
    private String ftColor;
    private String textColor;
    private Integer sourceType;

    @JsonProperty("outlineColor")
    private String outlineColor;
    @JsonProperty("outlineWidth")
    private Integer outlineWidth;
    @JsonProperty("outlineColor2")
    private String outlineColor2;
    @JsonProperty("outlineWidth2")
    private Integer outlineWidth2;

    @JsonProperty("shadowDx")
    private Integer shadowDx;
    @JsonProperty("shadowDy")
    private Integer shadowDy;
    @JsonProperty("shadowRadius")
    private Integer shadowRadius;
    @JsonProperty("shadowColor")
    private String shadowColor;

    private Integer speed;
    private Integer isVertical;
    private Integer textAlign;
    /**
     * 垂直居中（Type=5 多行文本）：0=否，1=是
     */
    private Integer verticalAlign;
    private Integer isHeadConnectTail;
    private Integer wordSpacing;
    private Integer beGlaring;
    private Integer textX;
    private Integer textY;
    @JsonProperty("bShowText")
    private Integer bShowText;
    @JsonProperty("bShowPic")
    private Integer bShowPic;
    /**
     * FIXME[属性名存疑] 首字母大小写
     */
    @JsonProperty("textGradient")
    @JsonAlias({"textGradient", "TextGradient"})
    private TextGradient textGradient;

    // ══════ 文本 滚动 ══════
    private Integer isScrollByTime;
    private Long playLength;
    private Integer repeatCount;
    private Integer ifSpeedByFrame;
    private Integer speedByFrame;
    private Integer moveType;
    private Integer showStyle;

    @JsonProperty("prevfix")
    private String prevfix;
    @JsonProperty("suffix")
    private String suffix;

    private String prefix;
    private Integer style;
    private Integer isMultiLine;
    @JsonProperty("base64Pages")
    private Object base64Pages;
    @JsonProperty("forceSinglePage")
    private Integer forceSinglePage;

    // ══════ 多图片 ══════
    private MultiPicInfo multiPicInfo;
    private Object scrollPicInfo;

    // ══════ 数字时钟 (Type=9) ══════
    private Integer isAnolog;
    private DigitalClock digitalClock;
    private Integer centeralAlign;
    /**
     * FIXME[类型存疑] 字符串|数字
     */
    @JsonSerialize(using = BigDecimalWithScaleSerializer.class)
    private BigDecimal timeZone;
    private String zoneDescripID;
    private Integer zoneBias;

    // ══════ 模拟时钟 (Type=7) ══════
    private AnologClock anologClock;
    private ClockFont clockFont;
    private HhourScale hhourScale;
    private HhourScale minuteScale;

    // ══════ 精美时钟 (Type=16) ══════
    @JsonProperty("exquisiteAnalogClock")
    private ExquisiteAnalogClock exquisiteAnalogClock;

    // ══════ 网页/流媒体 (Type=27) ══════
    private String url;
    private Integer isLocal;

    // ══════ 气象/计时器/传感器/夏令时 ══════
    private Integer weatherType;
    private String city;
    private String regionName;
    private String regionCode;
    private Integer serverType;
    private Integer isShowWeather;
    private Integer isShowTemperature;
    private Integer isShowWind;
    private Integer isShowAir;
    private Integer isShowColdIndex;
    private Integer isShowHumidity;
    private Integer isShowTemperatureDaynight;
    @JsonProperty("bShowAsFahrenheit")
    private Integer bShowAsFahrenheit;
    private String weatherPrefix;
    private String temperaturePrefix;
    private String windPrefix;
    private String airPrefix;
    private String coldIndex;
    private String humidity;
    private String temperaturePrefixDaynight;

    private Integer timerType;
    private String startTime;
    private String endTime;
    private String endDateTime;
    private Integer beToEndTime;
    private String dayCountColor;
    private String hourCountColor;
    private String minuteCountColor;
    @JsonProperty("secondCountColor")
    private String secondCountColor;
    private Integer isShowDayCount;
    private Integer isShowHourCount;
    private Integer isShowMinuteCount;
    private Integer isShowSecondCount;

    private String sensorId;
    private String sensorName;

    private DaylightDate daylightStart;
    private DaylightDate daylightEnd;
    private Integer daylightZone;
    private Integer daylightBias;
    @JsonProperty("flags")
    private Integer flags;
}
