package com.gateway.device.core.service;

import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.expand.NovaViplexCoreCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.font.GeneralFont;
import com.gateway.device.protocol.common.file.MediaFileEntry;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.model.params.*;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 对外 Map 参数到协议层 CommandParams 的适配器。
 */
@Slf4j
@Component
public class CommandParamsMapper {

    public CommandParams map(DeviceCapability<?> capability, Map<String, Object> source) {
        if (capability == null) {
            return EmptyParams.INSTANCE;
        }
        Map<String, Object> params = source == null ? Collections.emptyMap() : source;
        if (EmptyParams.class.equals(capability.paramsType())) {
            return EmptyParams.INSTANCE;
        }
        if (is(capability, CommonDeviceCapability.IMAGE_LIST_QUERY)
                || is(capability, CommonDeviceCapability.TEXT_LIST_QUERY)
                || is(capability, CommonDeviceCapability.VIDEO_LIST_QUERY)
                || is(capability, JetFileIICapability.NMG_FILE_LIST_QUERY)
                || is(capability, JetFileIICapability.PMG_FILE_LIST_QUERY)
                || is(capability, JetFileIICapability.QST_FILE_LIST_QUERY)) {
            return listQueryParams(params);
        }
        if (is(capability, CommonDeviceCapability.IMAGE_UPLOAD)
                || is(capability, CommonDeviceCapability.VIDEO_UPLOAD)
                || is(capability, JetFileIICapability.NMG_FILE_UPLOAD)
                || is(capability, JetFileIICapability.PMG_FILE_UPLOAD)
                || is(capability, JetFileIICapability.QST_FILE_UPLOAD)) {
            return mediaUploadParams(params);
        }
        if (is(capability, CommonDeviceCapability.TEXT_UPLOAD)) {
            return textUploadParams(params);
        }
        if (is(capability, CommonDeviceCapability.IMAGE_DOWNLOAD)
                || is(capability, CommonDeviceCapability.TEXT_DOWNLOAD)
                || is(capability, CommonDeviceCapability.VIDEO_DOWNLOAD)
                || is(capability, JetFileIICapability.NMG_FILE_DOWNLOAD)
                || is(capability, JetFileIICapability.PMG_FILE_DOWNLOAD)
                || is(capability, JetFileIICapability.QST_FILE_DOWNLOAD)) {
            return fileDownloadParams(params);
        }
        if (is(capability, CommonDeviceCapability.IMAGE_DELETE)
                || is(capability, CommonDeviceCapability.TEXT_DELETE)
                || is(capability, CommonDeviceCapability.VIDEO_DELETE)
                || is(capability, JetFileIICapability.NMG_FILE_DELETE)
                || is(capability, JetFileIICapability.PMG_FILE_DELETE)
                || is(capability, JetFileIICapability.QST_FILE_DELETE)) {
            return fileDeleteParams(params);
        }
        if (is(capability, CommonDeviceCapability.MEDIA_MULTI_UPLOAD)) {
            return mediaMultiUploadParams(params);
        }
        if (is(capability, CommonDeviceCapability.PLAYLIST_GET)) {
            return PlaylistGetParams.builder()
                    .sysFile(stringValue(params, "sysFile"))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.PLAYLIST_SET)) {
            return PlaylistSetParams.builder()
                    .identifier(firstString(params, "identifier", "playlistId"))
                    .name(firstString(params, "name", "playlistName"))
                    .paths(stringList(params.get("paths")))
                    .checkExistence(booleanValue(params, "checkExistence", false))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.SCREEN_BLACKOUT)) {
            return ScreenBlackoutParams.builder()
                    .blackout(firstBoolean(params, true, "blackout", "enabled", "on"))
                    .build();
        }
        if (is(capability, JetFileIICapability.COLOR_TEST)) {
            return ColorTestParams.builder()
                    .enabled(firstBoolean(params, true, "enabled", "on"))
                    .color(colorValue(params.get("color")))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.BRIGHTNESS_SET)) {
            Integer ratio = firstInteger(params, "ratio", "brightness", "level");
            return BrightnessSetParams.builder()
                    .action(ratio == null
                            ? BrightnessSetParams.BrightnessAction.GET
                            : BrightnessSetParams.BrightnessAction.SET)
                    .ratio(ratio == null ? 80 : ratio)
                    .build();
        }
        if (is(capability, CommonDeviceCapability.TIME_SYNC)) {
            return TimeSyncParams.builder()
                    .targetTime(localDateTimeValue(firstValue(params, "targetTime", "time")))
                    .timeZone(zoneIdValue(params.get("timeZone")))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.NTP_SET)) {
            return NtpSetParams.builder()
                    .ntpServer(firstString(params, "ntpServer", "server"))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.DEVICE_NETWORK_IP_SET)) {
            return IpConfigParams.builder()
                    .deviceId(stringValue(params, "deviceId"))
                    .ip(stringValue(params, "ip"))
                    .mask(firstString(params, "mask", "netmask", "subnetMask"))
                    .gateway(stringValue(params, "gateway"))
                    .dns1(dnsValue(params, 0, "dns1", "primaryDns"))
                    .dns2(dnsValue(params, 1, "dns2", "secondaryDns"))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.SCREEN_ATTRIBUTE_SET)) {
            return ScreenAttributeParams.builder()
                    .id(intValue(params, "id", 0))
                    .screenSource(intValue(params, "screenSource", 1))
                    .xCount(intValue(params, "xCount", 1))
                    .yCount(intValue(params, "yCount", 1))
                    .xOffset(intValue(params, "xOffset", 0))
                    .yOffset(intValue(params, "yOffset", 0))
                    .portNumber(intValue(params, "portNumber", 1))
                    .orders(integerList(params.get("orders")))
                    .width(firstInteger(params, "width"))
                    .height(firstInteger(params, "height"))
                    .build();
        }
        if (is(capability, CommonDeviceCapability.VOLUME_SET)) {
            return VolumeSetParams.builder()
                    .ratio(intValue(params, "ratio", 60))
                    .build();
        }
        if (is(capability, NovaViplexCoreCapability.DISPLAY_RESOLUTION_SET)) {
            return ResolutionSetParams.builder()
                    .displayMode(intValue(params, "displayMode", 1))
                    .width(firstInteger(params, "width"))
                    .height(firstInteger(params, "height"))
                    .build();
        }
        // ── v2 新增：FONTS_SYNC 字体同步 ──
        if (is(capability, CommonDeviceCapability.FONTS_SYNC)) {
            return FontSyncParams.builder()
                    .jetFileIIFonts(jetFileIIFontList(params.get("jetFileIIFonts")))
                    .fonts(generalFontList(firstValue(params, "fonts", "novaStarFonts")))
                    .build();
        }
        // ── v2 新增：FONTS_GET 查询字体列表（EmptyParams）已在上方通用分支覆盖 ──
        // ── v2 新增：AP_NETWORK_SWITCH AP 热点开关 ──
        if (is(capability, CommonDeviceCapability.DEVICE_NETWORK_AP_SWITCH)) {
            return ApNetworkSwitchParams.builder()
                    .enable(firstBoolean(params, true, "enable", "enabled", "on"))
                    .build();
        }
        throw new IllegalArgumentException("暂不支持该能力的参数转换: " + capability.name());
    }

    private ListQueryParams listQueryParams(Map<String, Object> params) {
        return ListQueryParams.builder()
                .filter(params.get("filter"))
                .path(stringValue(params, "path"))
                .partition(partitionValue(params.get("partition")))
                .build();
    }

    private MediaUploadParams mediaUploadParams(Map<String, Object> params) {
        return MediaUploadParams.builder()
                .data(bytesValue(params.get("data")))
                .fileName(firstString(params, "fileName", "label"))
                .width(firstInteger(params, "width"))
                .height(firstInteger(params, "height"))
                .chunkSize(intValue(params, "chunkSize", 1024))
                .remotePath(stringValue(params, "remotePath"))
                .partition(partitionValue(params.get("partition")))
                .build();
    }

    private TextUploadParams textUploadParams(Map<String, Object> params) {
        return TextUploadParams.builder()
                .text(stringValue(params, "text"))
                .data(bytesValue(params.get("data")))
                .width(firstInteger(params, "width"))
                .height(firstInteger(params, "height"))
                .partition(partitionValue(params.get("partition")))
                .textStyle(params.get("textStyle"))
                .build();
    }

    private FileDownloadParams fileDownloadParams(Map<String, Object> params) {
        return FileDownloadParams.builder()
                .fileName(firstString(params, "fileName", "label"))
                .remotePath(stringValue(params, "remotePath"))
                .partition(partitionValue(params.get("partition")))
                .build();
    }

    private FileDeleteParams fileDeleteParams(Map<String, Object> params) {
        return FileDeleteParams.builder()
                .fileName(firstString(params, "fileName", "label"))
                .partition(partitionValue(params.get("partition")))
                .build();
    }

    private MediaMultiUploadParams mediaMultiUploadParams(Map<String, Object> params) {
        return MediaMultiUploadParams.builder()
                .mediaFiles(mediaFileEntries(params.get("mediaFiles")))
                .width(firstInteger(params, "width"))
                .height(firstInteger(params, "height"))
                .build();
    }

    private boolean is(DeviceCapability<?> left, DeviceCapability<?> right) {
        return left == right || left.name().equals(right.name());
    }

    private Object firstValue(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            if (params.containsKey(key)) {
                return params.get(key);
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> params, String... keys) {
        Object value = firstValue(params, keys);
        return value == null ? null : String.valueOf(value);
    }

    private String dnsValue(Map<String, Object> params, int index, String... keys) {
        String direct = firstString(params, keys);
        if (direct != null) {
            return direct;
        }
        List<String> dnsList = stringList(params.get("dns"));
        return dnsList != null && dnsList.size() > index ? dnsList.get(index) : null;
    }

    private String stringValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer firstInteger(Map<String, Object> params, String... keys) {
        Object value = firstValue(params, keys);
        return integerValue(value);
    }

    private int intValue(Map<String, Object> params, String key, int defaultValue) {
        Integer value = integerValue(params.get(key));
        return value == null ? defaultValue : value;
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private boolean booleanValue(Map<String, Object> params, String key, boolean defaultValue) {
        return booleanValue(params.get(key), defaultValue);
    }

    private boolean firstBoolean(Map<String, Object> params, boolean defaultValue, String... keys) {
        Object value = firstValue(params, keys);
        return booleanValue(value, defaultValue);
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Partition partitionValue(Object value) {
        if (value == null) {
            return Partition.D;
        }
        if (value instanceof Partition) {
            return (Partition) value;
        }
        if (value instanceof Number) {
            Partition partition = Partition.ofCode(((Number) value).intValue());
            return partition == null ? Partition.D : partition;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Partition.D;
        }
        if (text.length() == 1) {
            Partition partition = Partition.ofDrive(Character.toUpperCase(text.charAt(0)));
            return partition == null ? Partition.D : partition;
        }
        try {
            return Partition.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Partition.D;
        }
    }

    /**
     * 字节参数转换 —— 支持 byte[] / Base64 字符串 / List&lt;Number&gt; 三种输入形式。
     * <p>
     * P2 增强：外部 JSON 中 data 字段可能传入 Base64 编码字符串或整数列表，
     * 本方法统一适配，避免上层手动转换。
     * </p>
     */
    private byte[] bytesValue(Object value) {
        if (value == null) {
            return null;
        }
        // 直接传入 byte[]（反序列化时 Jackson 可能保留原始字节）
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        // Base64 字符串
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Base64.getDecoder().decode(text);
            } catch (IllegalArgumentException e) {
                // 不是合法 Base64，尝试当作短字符串直接转字节
                return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        // List<Number> 字节数组形式（如 [72, 101, 108, 108, 111]）
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return new byte[0];
            }
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number) {
                    bytes[i] = ((Number) item).byteValue();
                } else {
                    // 无法转为字节，返回 null
                    return null;
                }
            }
            return bytes;
        }
        return null;
    }

    private ColorTestParams.Color colorValue(Object value) {
        if (value == null) {
            return ColorTestParams.Color.RED;
        }
        if (value instanceof ColorTestParams.Color) {
            return (ColorTestParams.Color) value;
        }
        try {
            return ColorTestParams.Color.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ColorTestParams.Color.RED;
        }
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        if (text.contains(" ")) {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.parse(text + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private ZoneId zoneIdValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ZoneId) {
            return (ZoneId) value;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : ZoneId.of(text);
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?>) {
            List<String> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    list.add(String.valueOf(item));
                }
            }
            return list;
        }
        return Collections.singletonList(String.valueOf(value));
    }

    private List<Integer> integerList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?>) {
            List<Integer> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                Integer integer = integerValue(item);
                if (integer != null) {
                    list.add(integer);
                }
            }
            return list;
        }
        Integer single = integerValue(value);
        return single == null ? null : Collections.singletonList(single);
    }

    private List<MediaFileEntry> mediaFileEntries(Object value) {
        if (!(value instanceof List<?>)) {
            return null;
        }
        List<MediaFileEntry> entries = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            entries.add(MediaFileEntry.builder()
                    .order(integerValue(map.get("order")) == null ? 0 : integerValue(map.get("order")))
                    .data(bytesValue(map.get("data")))
                    .fileName(map.get("fileName") == null ? null : String.valueOf(map.get("fileName")))
                    .mediaType(mediaTypeValue(map.get("mediaType")))
                    .duration(integerValue(map.get("duration")) == null ? 10000 : integerValue(map.get("duration")))
                    .build());
        }
        return entries;
    }

    private MediaType mediaTypeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof MediaType) {
            return (MediaType) value;
        }
        try {
            return MediaType.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 将 JSON 中的 JetFileIIFont 列表（字符串名称列表）转为枚举列表。
     * <p>输入格式示例：{@code ["EN_16x9", "CN_24x24"]}</p>
     */
    private List<JetFileIIFont> jetFileIIFontList(Object value) {
        if (value == null) {
            return null;
        }
        List<String> names = stringList(value);
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<JetFileIIFont> fonts = new ArrayList<>();
        for (String name : names) {
            try {
                fonts.add(JetFileIIFont.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warn("[CommandParamsMapper] 未知 JetFileIIFont 名称: {}, 忽略", name);
            }
        }
        return fonts.isEmpty() ? null : fonts;
    }

    /**
     * 将 JSON 中的 GeneralFont 列表（兼容旧 novaStarFonts 字段）转为枚举列表。
     * <p>输入格式示例：{@code ["SIMHEI", "SIMSUN"]}</p>
     */
    private List<GeneralFont> generalFontList(Object value) {
        if (value == null) {
            return null;
        }
        List<String> names = stringList(value);
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<GeneralFont> fonts = new ArrayList<>();
        for (String name : names) {
            try {
                fonts.add(GeneralFont.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                GeneralFont font = GeneralFont.ofFile(name);
                if (font != null) {
                    fonts.add(font);
                } else {
                    log.warn("[CommandParamsMapper] 未知 GeneralFont 名称: {}, 忽略", name);
                }
            }
        }
        return fonts.isEmpty() ? null : fonts;
    }
}
