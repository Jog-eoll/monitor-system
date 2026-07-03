package com.monitorplatform.log.service.impl;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.common.exception.BusinessException;
import com.monitorplatform.log.dto.TimelineQueryDTO;
import com.monitorplatform.log.mapper.ExistingLogQueryMapper;
import com.monitorplatform.log.service.TimelineQueryService;
import com.monitorplatform.log.vo.TimelineEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TimelineQueryServiceImpl implements TimelineQueryService {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 1000;

    private final ExistingLogQueryMapper existingLogQueryMapper;

    @Value("${log.timeline-default-days:7}")
    private long timelineDefaultDays;

    @Override
    public List<TimelineEventVO> timeline(TimelineQueryDTO queryDTO) {
        TimelineQueryDTO query = queryDTO == null ? new TimelineQueryDTO() : queryDTO;
        validateAtLeastOneKey(query);
        applyDefaultTimeRange(query);
        int limit = normalizeLimit(query.getLimit());

        List<TimelineEventVO> timeline = new ArrayList<TimelineEventVO>();
        append(timeline, existingLogQueryMapper.listDiagnosticTimeline(query, limit));
        append(timeline, existingLogQueryMapper.listOperationTimeline(query, limit));
        append(timeline, existingLogQueryMapper.listContentTimeline(query, limit));
        append(timeline, existingLogQueryMapper.listAlarmTimeline(query, limit));
        append(timeline, existingLogQueryMapper.listDispatchTimeline(query, limit));

        timeline.sort(Comparator.comparing(TimelineEventVO::getEventTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        if (timeline.size() > limit) {
            return new ArrayList<TimelineEventVO>(timeline.subList(0, limit));
        }
        return timeline;
    }

    private void validateAtLeastOneKey(TimelineQueryDTO query) {
        boolean hasKey = StringUtils.hasText(query.getTraceId())
                || StringUtils.hasText(query.getContentId())
                || query.getChainId() != null
                || StringUtils.hasText(query.getSourceIp())
                || StringUtils.hasText(query.getBoardIp())
                || StringUtils.hasText(query.getCertSerialNo())
                || StringUtils.hasText(query.getAlarmId());
        if (!hasKey) {
            throw new BusinessException(400, "timeline query needs at least one key");
        }
    }

    private void applyDefaultTimeRange(TimelineQueryDTO query) {
        if (query.getEndTime() == null) {
            query.setEndTime(LocalDateTime.now());
        }
        if (query.getStartTime() == null) {
            query.setStartTime(query.getEndTime().minusDays(timelineDefaultDays));
        }
        if (query.getStartTime().isBefore(query.getEndTime().minusDays(7))) {
            throw new BusinessException(400, "timeline range cannot exceed 7 days");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void append(List<TimelineEventVO> timeline, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            timeline.add(toTimelineEvent(row));
        }
    }

    private TimelineEventVO toTimelineEvent(Map<String, Object> row) {
        TimelineEventVO vo = new TimelineEventVO();
        vo.setEventTime(toLocalDateTime(row.get("eventTime")));
        vo.setSource(toString(row.get("source")));
        vo.setEventType(toString(row.get("eventType")));
        vo.setEventLevel(toString(row.get("eventLevel")));
        vo.setStage(toString(row.get("stage")));
        vo.setTraceId(toString(row.get("traceId")));
        vo.setChainId(toLong(row.get("chainId")));
        vo.setChainCode(toString(row.get("chainCode")));
        vo.setContentId(toString(row.get("contentId")));
        vo.setSourceIp(toString(row.get("sourceIp")));
        vo.setBoardIp(toString(row.get("boardIp")));
        vo.setOperatorName(toString(row.get("operatorName")));
        vo.setClientIp(toString(row.get("clientIp")));
        vo.setCertSerialNo(toString(row.get("certSerialNo")));
        vo.setResultStatus(toString(row.get("resultStatus")));
        vo.setSummary(toString(row.get("summary")));
        vo.setErrorMessage(toString(row.get("errorMessage")));
        vo.setRefTable(toString(row.get("refTable")));
        vo.setRefId(toString(row.get("refId")));
        vo.setDetail(parseDetail(row.get("detailJson")));
        return vo;
    }

    private Map<String, Object> parseDetail(Object detailJson) {
        String json = toString(detailJson);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, LinkedHashMap.class);
        } catch (Exception e) {
            Map<String, Object> detail = new LinkedHashMap<String, Object>();
            detail.put("raw", json);
            return detail;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Date) {
            return LocalDateTime.ofInstant(((Date) value).toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
