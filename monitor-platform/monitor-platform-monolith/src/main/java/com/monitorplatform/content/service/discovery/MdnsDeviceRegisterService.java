package com.monitorplatform.content.service.discovery;

import com.monitorplatform.content.entity.discovery.DiscoveredService;
import com.monitorplatform.device.entity.dto.DeviceRegisterDTO;
import com.monitorplatform.device.service.DeviceAutoRegisterService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class MdnsDeviceRegisterService {

    private static final Set<String> SUPPORTED_DEVICE_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "publish_gateway",
                    "terminal_encrypt_gateway",
                    "publish_server"
            )));

    @Resource
    private JmDNSServiceConsumer jmdnsConsumer;

    @Resource
    private DeviceAutoRegisterService deviceAutoRegisterService;

    public MdnsRegisterResult registerScannedDevices() {
        List<DiscoveredService> services = jmdnsConsumer.scanServices();
        MdnsRegisterResult result = new MdnsRegisterResult();
        result.setScanned(services == null ? 0 : services.size());

        if (services == null || services.isEmpty()) {
            return result;
        }

        for (DiscoveredService service : services) {
            MdnsRegisterItem item = buildBaseItem(service);
            try {
                DeviceRegisterDTO dto = toRegisterDto(service);
                item.setDeviceType(dto.getDeviceType());
                item.setInstanceId(dto.getInstanceId());
                item.setHost(dto.getHost());
                item.setPort(dto.getPort());

                boolean success = deviceAutoRegisterService.register(dto);
                if (success) {
                    item.setResult("REGISTERED");
                    item.setMessage("registered");
                    result.incrementRegistered();
                } else {
                    item.setResult("FAILED");
                    item.setMessage("device register service returned false");
                    result.incrementFailed();
                }
            } catch (MdnsRegisterSkipException e) {
                item.setResult("SKIPPED");
                item.setMessage(e.getMessage());
                result.incrementSkipped();
            } catch (Exception e) {
                item.setResult("FAILED");
                item.setMessage(e.getMessage());
                result.incrementFailed();
                log.warn("[JmDNS] register scanned service failed: name={}, host={}, port={}, error={}",
                        item.getName(), item.getHost(), item.getPort(), e.getMessage(), e);
            }
            result.getItems().add(item);
        }

        return result;
    }

    private DeviceRegisterDTO toRegisterDto(DiscoveredService service) {
        if (service == null) {
            throw new MdnsRegisterSkipException("service is null");
        }

        Map<String, String> props = service.getProperties() == null
                ? Collections.emptyMap() : service.getProperties();

        String deviceType = trim(props.get("deviceType"));
        if (!StringUtils.hasText(deviceType)) {
            throw new MdnsRegisterSkipException("missing deviceType");
        }
        deviceType = deviceType.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_DEVICE_TYPES.contains(deviceType)) {
            throw new MdnsRegisterSkipException("unsupported deviceType: " + deviceType);
        }

        String host = firstText(props.get("host"), service.getHostAddress());
        if (!StringUtils.hasText(host)) {
            throw new MdnsRegisterSkipException("missing host");
        }

        int port = service.getPort();
        if (port <= 0 || port > 65535) {
            throw new MdnsRegisterSkipException("invalid port: " + port);
        }

        DeviceRegisterDTO dto = new DeviceRegisterDTO();
        dto.setServiceName(firstText(service.getName(), deviceType + "-" + host + "-" + port));
        dto.setInstanceId(resolveInstanceId(service, props, deviceType, host, port));
        dto.setHost(host);
        dto.setPort(port);
        dto.setDeviceType(deviceType);
        dto.setMacAddress(firstText(props.get("macAddress"), props.get("mac"), props.get("mac-address")));
        dto.setLocation(trim(props.get("location")));
        dto.setVersion(trim(props.get("version")));
        dto.setManufacturer(trim(props.get("manufacturer")));
        dto.setModel(trim(props.get("model")));
        dto.setRemark(trim(props.get("remark")));
        return dto;
    }

    private String resolveInstanceId(DiscoveredService service, Map<String, String> props,
                                     String deviceType, String host, int port) {
        String configuredId = firstText(
                props.get("deviceId"),
                props.get("clientId"),
                props.get("instanceId"));
        if (StringUtils.hasText(configuredId)) {
            return configuredId;
        }

        String hostToken = host.replace('.', '-').replace(':', '-');
        return "mdns-" + deviceType + "-" + hostToken + "-" + port;
    }

    private MdnsRegisterItem buildBaseItem(DiscoveredService service) {
        MdnsRegisterItem item = new MdnsRegisterItem();
        if (service == null) {
            return item;
        }
        item.setName(service.getName());
        item.setHost(service.getHostAddress());
        item.setPort(service.getPort());
        Map<String, String> props = service.getProperties();
        if (props != null) {
            item.setDeviceType(props.get("deviceType"));
            item.setInstanceId(firstText(props.get("deviceId"), props.get("clientId"), props.get("instanceId")));
        }
        return item;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trim(value);
            if (StringUtils.hasText(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static class MdnsRegisterSkipException extends RuntimeException {
        MdnsRegisterSkipException(String message) {
            super(message);
        }
    }

    @Data
    public static class MdnsRegisterResult {
        private int scanned;
        private int registered;
        private int skipped;
        private int failed;
        private List<MdnsRegisterItem> items = new ArrayList<>();

        void incrementRegistered() {
            registered++;
        }

        void incrementSkipped() {
            skipped++;
        }

        void incrementFailed() {
            failed++;
        }
    }

    @Data
    public static class MdnsRegisterItem {
        private String name;
        private String instanceId;
        private String deviceType;
        private String host;
        private Integer port;
        private String result;
        private String message;
    }
}
