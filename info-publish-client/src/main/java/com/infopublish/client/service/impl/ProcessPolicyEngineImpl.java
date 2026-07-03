package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.ProcessPolicyProperties;
import com.infopublish.client.service.ProcessInfoResolver;
import com.infopublish.client.service.ProcessPolicyEngine;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Evaluates process whitelist in shadow mode only.
 */
@Service
public class ProcessPolicyEngineImpl implements ProcessPolicyEngine {

    @Resource
    private ProcessPolicyProperties properties;

    private final AtomicLong allowCount = new AtomicLong();
    private final AtomicLong denyCount = new AtomicLong();
    private final AtomicLong unknownCount = new AtomicLong();

    @Override
    public PolicyDecision decide(ProcessInfoResolver.ProcessInfo processInfo) {
        if (!properties.isEnabled()) {
            allowCount.incrementAndGet();
            return PolicyDecision.allow("PROCESS_POLICY_DISABLED", false);
        }
        if (processInfo == null || !processInfo.isAvailable()) {
            unknownCount.incrementAndGet();
            return PolicyDecision.unknown(processInfo != null ? processInfo.getReasonCode() : "PROCESS_INFO_MISSING");
        }

        String processName = normalize(processInfo.getProcessName());
        if (processName == null) {
            unknownCount.incrementAndGet();
            return PolicyDecision.unknown("PROCESS_NAME_EMPTY");
        }

        Set<String> whitelist = normalizedWhitelist();
        if (whitelist.isEmpty()) {
            unknownCount.incrementAndGet();
            return PolicyDecision.unknown("PROCESS_WHITELIST_EMPTY");
        }
        if (whitelist.contains(processName)) {
            allowCount.incrementAndGet();
            return PolicyDecision.allow("PROCESS_WHITELIST_MATCH", true);
        }

        denyCount.incrementAndGet();
        return PolicyDecision.deny("PROCESS_NOT_IN_WHITELIST");
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("blockUnknownProcess", properties.isBlockUnknownProcess());
        status.put("whitelist", properties.getWhitelist());
        status.put("allowCount", allowCount.get());
        status.put("denyCount", denyCount.get());
        status.put("unknownCount", unknownCount.get());
        return status;
    }

    private Set<String> normalizedWhitelist() {
        Set<String> set = new HashSet<>();
        if (properties.getWhitelist() == null) {
            return set;
        }
        for (String item : properties.getWhitelist()) {
            String normalized = normalize(item);
            if (normalized != null) {
                set.add(normalized);
            }
        }
        return set;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
