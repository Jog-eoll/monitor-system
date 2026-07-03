package com.infopublish.client.service;

import java.util.Map;

/**
 * Controls the local Windows ETW UDP traffic collector.
 */
public interface EtwTrafficCollectorService {

    /**
     * Starts the collector if it is not running.
     */
    Map<String, Object> startCollector();

    /**
     * Stops the collector if it is running.
     */
    Map<String, Object> stopCollector();

    /**
     * Returns current collector status and counters.
     */
    Map<String, Object> getStatus();
}
