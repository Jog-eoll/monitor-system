package com.infopublish.client.service;

import java.util.Map;

public interface PlatformConfigService {

    Map<String, String> getAllConfigs();

    String getConfig(String key);

    String getConfig(String key, String defaultValue);

    boolean updateConfig(String key, String value);

    void updateConfigs(Map<String, String> configs);
}
