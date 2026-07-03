package com.gateway.device.protocol.common;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateTimeFormatUtils {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeFormatUtils() {
    }

    public static String TimeFormat(LocalDateTime time) {
        LocalDateTime resultTime = time != null ? time : LocalDateTime.now();
        return resultTime.format(FMT);
    }

    public static ImmutablePair<ZoneId, Float> ZoneFormat(LocalDateTime time, ZoneId zoneId) {
        ZoneId resultZoneId = zoneId != null ? zoneId : ZoneId.systemDefault();
        LocalDateTime resultTime = time != null ? time : LocalDateTime.now();
        float zoneOffset = resultZoneId.getRules().getOffset(resultTime).getTotalSeconds() / 3600F;
        return ImmutablePair.of(resultZoneId, zoneOffset);
    }
}
