package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.api.DeviceComplianceValidator;
import com.gateway.device.protocol.base.jetfileii.standard.sys.ConfigSysFile;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.discovery.ComplianceRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * JetFileII 设备合规校验 —— 解析 SUB_READ_SYSINFO 响应并比对规则。
 */
@Slf4j
public class JetFileIIComplianceValidator implements DeviceComplianceValidator {

    private static final Set<String> HEX4_FIELDS = new HashSet<>(Collections.singletonList("softVer"));

    /**
     * 反射遍历 {@link ConfigSysFile.Info} 所有简单字段，展平为 fieldName → stringValue。
     *
     * <p>跳过 static 字段。softVer 格式化为 %04X，其余 Number 用 String.valueOf。</p>
     */
    static Map<String, String> toFieldMap(ConfigSysFile.Info info) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Field f : ConfigSysFile.Info.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(info);
                String key = f.getName();
                String val;
                if (v == null) {
                    val = "";
                } else if (HEX4_FIELDS.contains(key)) {
                    val = String.format("%04X", ((Number) v).intValue());
                } else if (v instanceof String) {
                    val = (String) v;
                } else {
                    val = String.valueOf(v);
                }
                m.put(key, val);
            } catch (IllegalAccessException ignored) {
            }
        }
        return m;
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.JET_FILE_II_STANDARD;
    }

    @Override
    public boolean validate(DeviceContext device, Object infoData, List<ComplianceRule> rules) {
        if (!(infoData instanceof byte[])) return false;
        try {
            ConfigSysFile.Info info = ConfigSysFile.parseText((byte[]) infoData);
            Map<String, String> fieldMap = toFieldMap(info);
            for (ComplianceRule rule : rules) {
                String actual = fieldMap.getOrDefault(rule.getField(), "");
                if (CollectionUtils.isEmpty(rule.getExpected())) continue;
                boolean matched = rule.getExpected().stream().anyMatch(actual::equals);
                if (!matched) {
                    log.debug("[{}] 合规不匹配: {}={} 期望: {}", device.getIp(),
                            rule.getField(), actual, rule.getExpected());
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("[{}] 合规校验异常: {}", device.getIp(), e.getMessage());
            return false;
        }
    }
}
