package com.gateway.device.protocol.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeFormatUtils {
    private DateTimeFormatUtils() {
    }

    public static String TimeFormat(LocalDateTime time) {
        return TimeFormat(time, FMT.GENERAL);
    }

    public static String TimeFormat(LocalDateTime time, FMT fmt) {
        LocalDateTime resultTime = time != null ? time : LocalDateTime.now();
        return resultTime.format(DateTimeFormatter.ofPattern(fmt != null ? fmt.getValue() : FMT.GENERAL.getValue()));
    }

    public static String TimeFormat(ZonedDateTime time) {
        return TimeFormat(time, FMT.ISO_8601_OFFSET);
    }

    public static String TimeFormat(ZonedDateTime time, FMT fmt) {
        ZonedDateTime resultTime = time != null ? time : ZonedDateTime.now();
        return resultTime.format(DateTimeFormatter.ofPattern(fmt != null ? fmt.getValue() : FMT.ISO_8601_OFFSET.getValue()));
    }

    public static ImmutablePair<ZoneId, Float> ZoneFormat(LocalDateTime time, ZoneId zoneId) {
        ZoneId resultZoneId = zoneId != null ? zoneId : ZoneId.systemDefault();
        LocalDateTime resultTime = time != null ? time : LocalDateTime.now();
        float zoneOffset = resultZoneId.getRules().getOffset(resultTime).getTotalSeconds() / 3600F;
        return ImmutablePair.of(resultZoneId, zoneOffset);
    }

    @Getter
    @AllArgsConstructor
    public enum FMT {
        GENERAL("yyyy-MM-dd HH:mm:ss"),
        ISO_8601_OFFSET("yyyy-MM-dd'T'HH:mm:ssZ"),
        ;
        private final String value;
    }
}
