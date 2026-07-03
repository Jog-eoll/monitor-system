package com.infopublish.client.service;

import com.infopublish.client.entity.dto.EtwTrafficEventDTO;

import java.util.Map;

/**
 * Accounts outbound UDP traffic reported by a local ETW collector.
 */
public interface EtwTrafficAccountingService {

    /**
     * Records one outbound UDP event reported by the local ETW collector.
     *
     * @param event ETW traffic event
     * @return structured recording result
     */
    Map<String, Object> recordOutboundEvent(EtwTrafficEventDTO event);

    /**
     * Queries aggregated traffic in the requested time window.
     *
     * @param windowStart inclusive window start timestamp in milliseconds
     * @param windowEnd exclusive window end timestamp in milliseconds
     * @param limit max number of records
     * @return structured query result
     */
    Map<String, Object> queryStats(long windowStart, long windowEnd, int limit);

    /**
     * Clears in-memory traffic counters.
     */
    void clear();
}
