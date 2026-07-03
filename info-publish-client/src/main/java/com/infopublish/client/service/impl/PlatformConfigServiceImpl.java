package com.infopublish.client.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.infopublish.client.entity.PlatformConfig;
import com.infopublish.client.repository.PlatformConfigMapper;
import com.infopublish.client.service.PlatformConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台配置管理服务
 */
@Slf4j
@Service
public class PlatformConfigServiceImpl implements PlatformConfigService {

    @Resource
    private PlatformConfigMapper platformConfigMapper;

    /**
     * 获取所有配置
     */
    public Map<String, String> getAllConfigs() {
        List<PlatformConfig> configs = platformConfigMapper.selectList(null);
        Map<String, String> result = new HashMap<>();
        for (PlatformConfig config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    /**
     * 获取单个配置
     */
    public String getConfig(String key) {
        LambdaQueryWrapper<PlatformConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformConfig::getConfigKey, key);
        PlatformConfig config = platformConfigMapper.selectOne(wrapper);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 获取单个配置（带默认值）
     */
    public String getConfig(String key, String defaultValue) {
        String value = getConfig(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 更新配置
     */
    public boolean updateConfig(String key, String value) {
        LambdaUpdateWrapper<PlatformConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PlatformConfig::getConfigKey, key)
                .set(PlatformConfig::getConfigValue, value)
                .set(PlatformConfig::getUpdateTime, LocalDateTime.now());
        int rows = platformConfigMapper.update(null, wrapper);

        if (rows == 0) {
            // 不存在则新建
            PlatformConfig config = new PlatformConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            platformConfigMapper.insert(config);
        }

        log.info("配置已更新: {}={}", key, value);
        return true;
    }

    /**
     * 批量更新配置
     */
    public void updateConfigs(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            updateConfig(entry.getKey(), entry.getValue());
        }
    }
}
