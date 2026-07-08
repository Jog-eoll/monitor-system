package com.gateway.device.core.controller;

import com.gateway.common.Result;
import com.gateway.device.core.service.AutoDiscoveryService;
import com.gateway.device.core.service.DeviceManagementService;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.RegistrationSource;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import com.gateway.device.protocol.model.discovery.ExplicitIpDiscoveredDevice;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 璁惧娉ㄥ唽琛ㄨ皟璇?杩愮淮鍏ュ彛銆? */
@RestController
@RequestMapping("/api/devices")
public class DeviceManagementController {

    private static final DeviceCapability<?> DEVICE_SEARCH_CAPABILITY = CommonDeviceCapability.DEVICE_SEARCH;

    private static final List<DeviceCapability<?>> JET_FILE_II_COMMON_CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
            DEVICE_SEARCH_CAPABILITY,
            CommonDeviceCapability.DEVICE_INFO_GET,
            CommonDeviceCapability.IMAGE_LIST_QUERY,
            CommonDeviceCapability.IMAGE_UPLOAD,
            CommonDeviceCapability.IMAGE_DOWNLOAD,
            CommonDeviceCapability.IMAGE_DELETE,
            CommonDeviceCapability.TEXT_LIST_QUERY,
            CommonDeviceCapability.TEXT_UPLOAD,
            CommonDeviceCapability.TEXT_DOWNLOAD,
            CommonDeviceCapability.TEXT_DELETE,
            CommonDeviceCapability.VIDEO_LIST_QUERY,
            CommonDeviceCapability.VIDEO_UPLOAD,
            CommonDeviceCapability.VIDEO_DOWNLOAD,
            CommonDeviceCapability.VIDEO_DELETE,
            CommonDeviceCapability.MEDIA_MULTI_UPLOAD,
            CommonDeviceCapability.PLAYLIST_GET,
            CommonDeviceCapability.PLAYLIST_SET,
            CommonDeviceCapability.PLAYLIST_CLEAR,
            CommonDeviceCapability.POWER_CONTROL_REBOOT,
            CommonDeviceCapability.SCREEN_BLACKOUT,
            JetFileIICapability.COLOR_TEST,
            CommonDeviceCapability.BRIGHTNESS_SET,
            CommonDeviceCapability.TIME_SYNC
    ));

    private static final List<DeviceCapability<?>> NOVA_VIPLEX_CORE_CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
            DEVICE_SEARCH_CAPABILITY,
            CommonDeviceCapability.DEVICE_INFO_GET,
            CommonDeviceCapability.IMAGE_UPLOAD,
            CommonDeviceCapability.IMAGE_DOWNLOAD,
            CommonDeviceCapability.TEXT_UPLOAD,
            CommonDeviceCapability.TEXT_DOWNLOAD,
            CommonDeviceCapability.VIDEO_UPLOAD,
            CommonDeviceCapability.VIDEO_DOWNLOAD,
            CommonDeviceCapability.MEDIA_MULTI_UPLOAD,
            CommonDeviceCapability.MEDIA_CLEAR,
            CommonDeviceCapability.PLAYLIST_GET,
            CommonDeviceCapability.PLAYLIST_SET,
            CommonDeviceCapability.PLAYLIST_CLEAR,
            CommonDeviceCapability.POWER_CONTROL_REBOOT,
            CommonDeviceCapability.SCREEN_BLACKOUT,
            CommonDeviceCapability.BRIGHTNESS_SET,
            CommonDeviceCapability.TIME_SYNC,
            CommonDeviceCapability.NTP_SET,
            CommonDeviceCapability.DEVICE_NETWORK_IP_SET,
            CommonDeviceCapability.SCREEN_ATTRIBUTE_SET
    ));

    private static final List<DeviceCapability<?>> COLOR_LIGHT_CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
            DEVICE_SEARCH_CAPABILITY,
            CommonDeviceCapability.DEVICE_INFO_GET,
            CommonDeviceCapability.IMAGE_LIST_QUERY,
            CommonDeviceCapability.IMAGE_UPLOAD,
            CommonDeviceCapability.IMAGE_DOWNLOAD,
            CommonDeviceCapability.IMAGE_DELETE,
            CommonDeviceCapability.TEXT_LIST_QUERY,
            CommonDeviceCapability.TEXT_UPLOAD,
            CommonDeviceCapability.TEXT_DOWNLOAD,
            CommonDeviceCapability.TEXT_DELETE,
            CommonDeviceCapability.VIDEO_LIST_QUERY,
            CommonDeviceCapability.VIDEO_UPLOAD,
            CommonDeviceCapability.VIDEO_DOWNLOAD,
            CommonDeviceCapability.VIDEO_DELETE,
            CommonDeviceCapability.MEDIA_MULTI_UPLOAD,
            CommonDeviceCapability.PLAYLIST_GET,
            CommonDeviceCapability.PLAYLIST_SET,
            CommonDeviceCapability.PLAYLIST_CLEAR,
            CommonDeviceCapability.POWER_CONTROL_REBOOT,
            CommonDeviceCapability.SCREEN_BLACKOUT,
            CommonDeviceCapability.BRIGHTNESS_SET,
            CommonDeviceCapability.TIME_SYNC
    ));

    private static final List<DeviceCapability<?>> JET_FILE_II_CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
            JetFileIICapability.NMG_FILE_LIST_QUERY,
            JetFileIICapability.NMG_FILE_UPLOAD,
            JetFileIICapability.NMG_FILE_DOWNLOAD,
            JetFileIICapability.NMG_FILE_DELETE,
            JetFileIICapability.PMG_FILE_LIST_QUERY,
            JetFileIICapability.PMG_FILE_UPLOAD,
            JetFileIICapability.PMG_FILE_DOWNLOAD,
            JetFileIICapability.PMG_FILE_DELETE,
            JetFileIICapability.QST_FILE_LIST_QUERY,
            JetFileIICapability.QST_FILE_UPLOAD,
            JetFileIICapability.QST_FILE_DOWNLOAD,
            JetFileIICapability.QST_FILE_DELETE
    ));

    private static final Map<String, DeviceCapability<?>> CAPABILITY_MAP = buildCapabilityMap();

    @Resource
    private DeviceManagementService deviceManagementService;

    @Resource
    private AutoDiscoveryService autoDiscoveryService;

    @GetMapping
    public Result<Collection<DeviceView>> list() {
        return Result.success(toViews(deviceManagementService.listAll()));
    }

    @GetMapping("/{deviceId}")
    public Result<DeviceView> get(@PathVariable String deviceId) {
        DeviceContext device = deviceManagementService.get(deviceId).orElse(null);
        if (device == null) {
            return Result.error("\u8bbe\u5907\u4e0d\u5b58\u5728: " + deviceId);
        }
        return Result.success(toView(device));
    }

    @PostMapping("/register")
    public Result<DeviceView> register(@RequestBody DeviceRegisterRequest request) {
        String deviceId = firstNonBlank(request.getDeviceId(), request.getIp());
        if (isBlank(deviceId)) {
            return Result.error("deviceId/ip \u4e0d\u80fd\u540c\u65f6\u4e3a\u7a7a");
        }
        if (isBlank(request.getIp())) {
            return Result.error("ip \u4e0d\u80fd\u4e3a\u7a7a");
        }

        DeviceVendor vendor = resolveVendor(request.getVendor());
        if (vendor == null) {
            return Result.error("\u4e0d\u652f\u6301\u7684 vendor: " + request.getVendor());
        }

        int effectivePort = request.getPort() == null ? defaultPort(vendor) : request.getPort();
        if (shouldUseProtocolRegistration(vendor, request)) {
            DeviceContext device = DeviceVendor.COLOR_LIGHT_STANDARD.equals(vendor)
                    ? registerColorLight(request.getIp().trim(), effectivePort)
                    : registerJetFileII(request.getIp().trim(), effectivePort);
            if (device == null || !device.isOnline() || !device.isLoggedIn()) {
                return Result.error(protocolRegistrationFailureMessage(vendor));
            }
            return Result.success("\u8bbe\u5907\u5df2\u6ce8\u518c", toView(device));
        }

        Map<String, Object> attributes = buildAttributes(request);
        DeviceContext device = DeviceContext.builder()
                .deviceId(deviceId)
                .ip(request.getIp().trim())
                .port(effectivePort)
                .vendor(vendor)
                .groupLabel(firstNonBlank(request.getGroupId(), request.getProductType()))
                .online(request.getOnline() == null || request.getOnline())
                .capabilities(resolveCapabilities(vendor, request.getCapabilities()))
                .attributes(attributes)
                .width(request.getWidth())
                .height(request.getHeight())
                .sn(firstNonBlank(request.getSn(), attr(attributes, "serialNo"), attr(attributes, "sn")))
                .macAddr(ProtocolConstant.formatMac(firstNonBlank(request.getMacAddr(), attr(attributes, "macAddr"), attr(attributes, "mac"))))
                .build();

        deviceManagementService.register(device);
        return Result.success("\u8bbe\u5907\u5df2\u6ce8\u518c", toView(device));
    }

    private boolean shouldUseProtocolRegistration(DeviceVendor vendor, DeviceRegisterRequest request) {
        return (DeviceVendor.JET_FILE_II_STANDARD.equals(vendor)
                || DeviceVendor.COLOR_LIGHT_STANDARD.equals(vendor))
                && (request.getOnline() == null || request.getOnline());
    }

    private DeviceContext registerJetFileII(String ip, int port) {
        return registerByProtocol(DeviceVendor.JET_FILE_II_STANDARD, ip, port);
    }

    private DeviceContext registerColorLight(String ip, int port) {
        return registerByProtocol(DeviceVendor.COLOR_LIGHT_STANDARD, ip, port);
    }

    private DeviceContext registerByProtocol(DeviceVendor vendor, String ip, int port) {
        DeviceVendorMapping mapping = new DeviceVendorMapping();
        mapping.setVendor(vendor);
        mapping.setPort(port);
        ExplicitIpDiscoveredDevice discoveredDevice = new ExplicitIpDiscoveredDevice(ip, port);
        return autoDiscoveryService.registerDevice(discoveredDevice, mapping, RegistrationSource.MANUAL_IP);
    }

    private String protocolRegistrationFailureMessage(DeviceVendor vendor) {
        if (DeviceVendor.COLOR_LIGHT_STANDARD.equals(vendor)) {
            return "\u8bbe\u5907\u6ce8\u518c\u5931\u8d25: ColorLight \u767b\u5f55\u6216\u8bfb\u53d6\u8bbe\u5907\u4fe1\u606f\u5931\u8d25";
        }
        return "\u8bbe\u5907\u6ce8\u518c\u5931\u8d25: JetFileII \u767b\u5f55\u6216\u8bfb\u53d6\u8bbe\u5907\u4fe1\u606f\u5931\u8d25";
    }

    @PostMapping("/scan")
    public Result<Map<String, Object>> scan() {
        autoDiscoveryService.scan();
        Collection<DeviceContext> devices = deviceManagementService.listAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", devices.size());
        result.put("devices", toViews(devices));
        return Result.success(result);
    }

    @PostMapping("/{deviceId}/online")
    public Result<String> markOnline(@PathVariable String deviceId) {
        deviceManagementService.markOnline(deviceId);
        return Result.success("\u8bbe\u5907\u5df2\u6807\u8bb0\u5728\u7ebf");
    }

    @PostMapping("/{deviceId}/offline")
    public Result<String> markOffline(@PathVariable String deviceId) {
        deviceManagementService.markOffline(deviceId);
        return Result.success("\u8bbe\u5907\u5df2\u6807\u8bb0\u79bb\u7ebf");
    }

    @DeleteMapping("/{deviceId}")
    public Result<String> remove(@PathVariable String deviceId) {
        deviceManagementService.remove(deviceId);
        return Result.success("\u8bbe\u5907\u5df2\u79fb\u9664");
    }

    private Set<DeviceCapability<?>> resolveCapabilities(DeviceVendor vendor, Set<String> configured) {
        Set<DeviceCapability<?>> capabilities = new LinkedHashSet<>();
        if (configured != null) {
            for (String item : configured) {
                DeviceCapability<?> capability = resolveCapability(item);
                if (capability != null) {
                    capabilities.add(capability);
                }
            }
        }
        if (!capabilities.isEmpty()) {
            return capabilities;
        }

        if (DeviceVendor.JET_FILE_II_STANDARD.equals(vendor)) {
            capabilities.addAll(JET_FILE_II_COMMON_CAPABILITIES);
            capabilities.addAll(JET_FILE_II_CAPABILITIES);
        } else if (DeviceVendor.NOVA_STAR_VIPLEX_CORE.equals(vendor)) {
            capabilities.addAll(NOVA_VIPLEX_CORE_CAPABILITIES);
        } else if (DeviceVendor.COLOR_LIGHT_STANDARD.equals(vendor)) {
            capabilities.addAll(COLOR_LIGHT_CAPABILITIES);
        }
        return capabilities;
    }

    private DeviceCapability<?> resolveCapability(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        return CAPABILITY_MAP.get(normalized);
    }

    private Map<String, Object> buildAttributes(DeviceRegisterRequest request) {
        Map<String, Object> attributes = new HashMap<>();
        if (request.getAttributes() != null) {
            attributes.putAll(request.getAttributes());
        }
        putIfNotBlank(attributes, "productType", request.getProductType());
        putIfNotBlank(attributes, "groupId", request.getGroupId());
        putIfNotBlank(attributes, "serialNo", request.getSn());
        putIfNotBlank(attributes, "macAddr", request.getMacAddr());
        putIfNotNull(attributes, "width", request.getWidth());
        putIfNotNull(attributes, "height", request.getHeight());
        return attributes;
    }

    private static Map<String, DeviceCapability<?>> buildCapabilityMap() {
        Map<String, DeviceCapability<?>> map = new HashMap<>();
        addCapabilities(map, JET_FILE_II_COMMON_CAPABILITIES);
        addCapabilities(map, NOVA_VIPLEX_CORE_CAPABILITIES);
        addCapabilities(map, JET_FILE_II_CAPABILITIES);
        addCapabilities(map, COLOR_LIGHT_CAPABILITIES);
        return Collections.unmodifiableMap(map);
    }

    private static void addCapabilities(Map<String, DeviceCapability<?>> map, Collection<DeviceCapability<?>> capabilities) {
        for (DeviceCapability<?> capability : capabilities) {
            map.put(capability.name(), capability);
        }
    }

    private DeviceVendor resolveVendor(String vendor) {
        if (isBlank(vendor)) {
            return DeviceVendor.JET_FILE_II_STANDARD;
        }
        String normalized = vendor.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        if ("QINGSONG".equals(normalized)
                || "QING_SONG".equals(normalized)
                || "JETFILEII".equals(normalized)
                || "JET_FILEII".equals(normalized)
                || "JET_FILE_II".equals(normalized)) {
            return DeviceVendor.JET_FILE_II_STANDARD;
        }
        if ("COLORLIGHT".equals(normalized) || "COLOR_LIGHT".equals(normalized)
                || "COLORIGHT".equals(normalized)) {
            return DeviceVendor.COLOR_LIGHT_STANDARD;
        }
        try {
            return DeviceVendor.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int defaultPort(DeviceVendor vendor) {
        if (DeviceVendor.COLOR_LIGHT_STANDARD.equals(vendor)) {
            return 8989;
        }
        return 9520;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (!isBlank(value)) {
            target.putIfAbsent(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.putIfAbsent(key, value);
        }
    }

    private String attr(Map<String, Object> attributes, String key) {
        Object value = attributes != null ? attributes.get(key) : null;
        return value == null ? null : value.toString();
    }

    private Collection<DeviceView> toViews(Collection<DeviceContext> devices) {
        if (devices == null || devices.isEmpty()) {
            return Collections.emptyList();
        }
        return devices.stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    private DeviceView toView(DeviceContext device) {
        if (device == null) {
            return null;
        }
        DeviceView view = new DeviceView();
        view.setDeviceId(device.getDeviceId());
        view.setIp(device.getIp());
        view.setPort(device.getPort());
        view.setVendor(device.getVendor() == null ? null : device.getVendor().name());
        view.setGroupLabel(device.getGroupLabel());
        view.setOnline(device.isOnline());
        view.setLoggedIn(device.isLoggedIn());
        view.setWidth(device.getWidth());
        view.setHeight(device.getHeight());
        view.setSn(device.getSn());
        view.setMacAddr(device.getMacAddr());
        view.setCapabilities(toCapabilityNames(device.getCapabilities()));
        view.setAttributes(device.getAttributes() == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(device.getAttributes()));
        return view;
    }

    private Set<String> toCapabilityNames(Set<DeviceCapability<?>> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Collections.emptySet();
        }
        return capabilities.stream()
                .filter(Objects::nonNull)
                .map(DeviceCapability::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Data
    public static class DeviceRegisterRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendor;
        private String productType;
        private String groupId;
        private Integer width;
        private Integer height;
        private String sn;
        private String macAddr;
        private Boolean online;
        private Set<String> capabilities;
        private Map<String, Object> attributes;
    }

    @Data
    public static class DeviceView implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendor;
        private String groupLabel;
        private boolean online;
        private boolean loggedIn;
        private Integer width;
        private Integer height;
        private String sn;
        private String macAddr;
        private Set<String> capabilities;
        private Map<String, Object> attributes;
    }
}
