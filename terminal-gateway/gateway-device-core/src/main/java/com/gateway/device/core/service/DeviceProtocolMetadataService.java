package com.gateway.device.core.service;

import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.params.EmptyParams;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class DeviceProtocolMetadataService {

    private static final List<DeviceVendor> VENDOR_ORDER = Arrays.asList(
            DeviceVendor.JET_FILE_II_STANDARD,
            DeviceVendor.NOVA_STAR_STANDARD,
            DeviceVendor.NOVA_STAR_VIPLEX_CORE,
            DeviceVendor.COLOR_LIGHT_STANDARD
    );

    private static final Map<String, String> COMMAND_CAPABILITY_MAP = commandCapabilityMap();

    private final ProtocolRouter protocolRouter;

    public DeviceProtocolMetadataService(ProtocolRouter protocolRouter) {
        this.protocolRouter = protocolRouter;
    }

    public Map<String, Object> metadata(String vendorFilter) {
        String normalizedVendor = normalizeVendor(vendorFilter);
        List<Map<String, Object>> vendors = new ArrayList<>();

        for (DeviceVendor vendor : VENDOR_ORDER) {
            if (normalizedVendor != null && !vendor.name().equals(normalizedVendor)) {
                continue;
            }
            vendors.add(buildVendorMetadata(vendor));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metadataVersion", "1.0");
        result.put("source", "terminal-gateway");
        result.put("generatedAt", System.currentTimeMillis());
        result.put("filter", trimToNull(vendorFilter));
        result.put("matched", normalizedVendor == null || !vendors.isEmpty());
        result.put("vendors", vendors);
        return result;
    }

    private Map<String, Object> buildVendorMetadata(DeviceVendor vendor) {
        VendorProtocolAdapter adapter = protocolRouter.findByVendor(vendor);
        Set<String> capabilityNames = capabilityNames(adapter);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("vendorCode", vendor.name());
        item.put("vendorName", displayName(vendor));
        item.put("aliases", aliases(vendor));
        item.put("productCode", productCode(vendor));
        item.put("productName", productName(vendor));
        item.put("defaultPort", VendorDefaultPort.getDefaultPort(vendor));
        item.put("transportType", adapter != null && adapter.transportType() != null
                ? adapter.transportType().name() : defaultTransportType(vendor));
        item.put("discoveryMode", discoveryMode(vendor));
        item.put("authMode", authMode(vendor));
        item.put("adapterAvailable", adapter != null);
        item.put("controlCommands", controlCommands(capabilityNames));
        item.put("capabilities", capabilities(adapter));
        return item;
    }

    private Set<String> capabilityNames(VendorProtocolAdapter adapter) {
        Set<String> names = new TreeSet<>();
        if (adapter == null) {
            return names;
        }
        for (DeviceCapability<?> capability : adapter.capabilities()) {
            if (capability != null) {
                names.add(capability.name());
            }
        }
        return names;
    }

    private List<Map<String, Object>> capabilities(VendorProtocolAdapter adapter) {
        if (adapter == null) {
            return Collections.emptyList();
        }
        List<DeviceCapability<?>> capabilities = new ArrayList<>(adapter.capabilities());
        capabilities.sort(Comparator.comparing(DeviceCapability::name));

        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceCapability<?> capability : capabilities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("capability", capability.name());
            item.put("paramsType", capability.paramsType().getSimpleName());
            item.put("paramsRequired", !EmptyParams.class.equals(capability.paramsType()));
            item.put("controlCommand", controlCommandFor(capability.name()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> controlCommands(Set<String> capabilityNames) {
        List<Map<String, Object>> commands = new ArrayList<>();
        for (Map.Entry<String, String> entry : COMMAND_CAPABILITY_MAP.entrySet()) {
            if (capabilityNames.contains(entry.getValue())) {
                commands.add(commandDefinition(entry.getKey(), entry.getValue()));
            }
        }
        return commands;
    }

    private Map<String, Object> commandDefinition(String command, String capability) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("command", command);
        item.put("capability", capability);
        item.put("params", paramsSchema(command));
        return item;
    }

    private Map<String, Object> paramsSchema(String command) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if ("BRIGHTNESS".equals(command)) {
            schema.put("required", Collections.singletonList(field("brightness", "integer", "0..100")));
            schema.put("optional", Collections.emptyList());
            schema.put("example", singleton("brightness", 80));
        } else if ("BLACKOUT".equals(command)) {
            schema.put("required", Collections.singletonList(field("enabled", "boolean", "true means blackout, false means resume")));
            schema.put("optional", Collections.emptyList());
            schema.put("example", singleton("enabled", Boolean.TRUE));
        } else if ("TIME_SYNC".equals(command)) {
            schema.put("required", Collections.emptyList());
            schema.put("optional", Collections.singletonList(field("time", "string", "ISO-8601, omitted means current gateway time")));
            schema.put("example", singleton("time", "2026-07-03T10:00:00+08:00"));
        } else if ("NTP_SET".equals(command)) {
            schema.put("required", Collections.singletonList(field("ntpServer", "string", "NTP server host or IP")));
            schema.put("aliases", singleton("server", "ntpServer"));
            schema.put("example", singleton("ntpServer", "192.168.113.10"));
        } else if ("DEVICE_IP_SET".equals(command)) {
            schema.put("required", Collections.singletonList(field("ip", "string", "target device IP")));
            schema.put("optional", Arrays.asList(
                    field("mask", "string", "subnet mask"),
                    field("gateway", "string", "gateway IP"),
                    field("dns", "string", "DNS server")
            ));
            Map<String, Object> aliases = new LinkedHashMap<>();
            aliases.put("netmask", "mask");
            aliases.put("subnetMask", "mask");
            schema.put("aliases", aliases);
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("ip", "192.168.113.239");
            example.put("mask", "255.255.255.0");
            example.put("gateway", "192.168.113.1");
            schema.put("example", example);
        } else if ("SCREEN_ATTRIBUTE_SET".equals(command)) {
            schema.put("required", Arrays.asList(
                    field("width", "integer", "screen width"),
                    field("height", "integer", "screen height")
            ));
            schema.put("optional", Arrays.asList(
                    field("xCount", "integer", "receiver card columns"),
                    field("yCount", "integer", "receiver card rows"),
                    field("portNumber", "integer", "output port number"),
                    field("xOffset", "integer", "x offset"),
                    field("yOffset", "integer", "y offset"),
                    field("orders", "array", "receiver card order list")
            ));
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("width", 96);
            example.put("height", 36);
            example.put("xCount", 1);
            example.put("yCount", 1);
            schema.put("example", example);
        } else if ("FONTS_SYNC".equals(command)) {
            schema.put("required", Collections.singletonList(field("jetFileIIFonts", "array", "or novaStarFonts")));
            schema.put("aliases", singleton("novaStarFonts", "jetFileIIFonts"));
            schema.put("example", singleton("jetFileIIFonts", Collections.singletonList("SimSun")));
        } else if ("AP_NETWORK_SWITCH".equals(command)) {
            schema.put("required", Collections.singletonList(field("enable", "boolean", "true means enable AP network")));
            Map<String, Object> aliases = new LinkedHashMap<>();
            aliases.put("enabled", "enable");
            aliases.put("on", "enable");
            schema.put("aliases", aliases);
            schema.put("example", singleton("enable", Boolean.TRUE));
        } else {
            schema.put("required", Collections.emptyList());
            schema.put("optional", Collections.emptyList());
            schema.put("example", Collections.emptyMap());
        }
        return schema;
    }

    private Map<String, Object> field(String name, String type, String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("type", type);
        field.put("description", description);
        return field;
    }

    private Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private String controlCommandFor(String capability) {
        for (Map.Entry<String, String> entry : COMMAND_CAPABILITY_MAP.entrySet()) {
            if (entry.getValue().equals(capability)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String normalizeVendor(String value) {
        String vendor = trimToNull(value);
        if (vendor == null) {
            return null;
        }
        String normalized = vendor.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        for (DeviceVendor item : VENDOR_ORDER) {
            if (item.name().equals(normalized)) {
                return item.name();
            }
            Set<String> aliasSet = new HashSet<>(aliases(item));
            if (aliasSet.contains(normalized)) {
                return item.name();
            }
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, String> commandCapabilityMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("QUERY_STATUS", CommonDeviceCapability.DEVICE_INFO_GET.name());
        map.put("BRIGHTNESS", CommonDeviceCapability.BRIGHTNESS_SET.name());
        map.put("BLACKOUT", CommonDeviceCapability.SCREEN_BLACKOUT.name());
        map.put("REBOOT", CommonDeviceCapability.POWER_CONTROL_REBOOT.name());
        map.put("TIME_SYNC", CommonDeviceCapability.TIME_SYNC.name());
        map.put("NTP_SET", CommonDeviceCapability.NTP_SET.name());
        map.put("DEVICE_IP_SET", CommonDeviceCapability.DEVICE_NETWORK_IP_SET.name());
        map.put("SCREEN_ATTRIBUTE_SET", CommonDeviceCapability.SCREEN_ATTRIBUTE_SET.name());
        map.put("FONTS_GET", CommonDeviceCapability.FONTS_GET.name());
        map.put("FONTS_SYNC", CommonDeviceCapability.FONTS_SYNC.name());
        map.put("AP_NETWORK_SWITCH", CommonDeviceCapability.DEVICE_NETWORK_AP_SWITCH.name());
        return Collections.unmodifiableMap(map);
    }

    private String displayName(DeviceVendor vendor) {
        switch (vendor) {
            case JET_FILE_II_STANDARD:
                return "QingSong JetFileII Standard";
            case NOVA_STAR_STANDARD:
                return "NovaStar Standard";
            case NOVA_STAR_VIPLEX_CORE:
                return "NovaStar ViplexCore";
            case COLOR_LIGHT_STANDARD:
                return "ColorLight Standard";
            default:
                return vendor.name();
        }
    }

    private String productCode(DeviceVendor vendor) {
        switch (vendor) {
            case JET_FILE_II_STANDARD:
                return "JETFILEII";
            case NOVA_STAR_STANDARD:
                return "NOVA_STANDARD";
            case NOVA_STAR_VIPLEX_CORE:
                return "NOVA_VIPLEX_CORE";
            case COLOR_LIGHT_STANDARD:
                return "COLORLIGHT";
            default:
                return vendor.name();
        }
    }

    private String productName(DeviceVendor vendor) {
        switch (vendor) {
            case JET_FILE_II_STANDARD:
                return "JetFileII";
            case NOVA_STAR_STANDARD:
                return "NovaStar UDP";
            case NOVA_STAR_VIPLEX_CORE:
                return "ViplexCore";
            case COLOR_LIGHT_STANDARD:
                return "ColorLight";
            default:
                return vendor.name();
        }
    }

    private List<String> aliases(DeviceVendor vendor) {
        switch (vendor) {
            case JET_FILE_II_STANDARD:
                return Arrays.asList("QINGSONG", "QING_SONG", "JETFILEII", "JET_FILEII", "JET_FILE_II");
            case NOVA_STAR_STANDARD:
                return Arrays.asList("NOVA", "NOVA_STANDARD", "NOVA_STAR");
            case NOVA_STAR_VIPLEX_CORE:
                return Arrays.asList("NOVA_VIPLEX", "NOVA_STAR_VIPLEX", "VIPLEX_CORE", "NOVA_STAR_VIPLEX_CORE");
            case COLOR_LIGHT_STANDARD:
                return Arrays.asList("COLORLIGHT", "COLOR_LIGHT", "COLORIGHT");
            default:
                return Collections.emptyList();
        }
    }

    private String defaultTransportType(DeviceVendor vendor) {
        switch (vendor) {
            case JET_FILE_II_STANDARD:
            case NOVA_STAR_STANDARD:
                return "UDP";
            case NOVA_STAR_VIPLEX_CORE:
                return "NATIVE_SDK";
            case COLOR_LIGHT_STANDARD:
                return "HTTP";
            default:
                return null;
        }
    }

    private String discoveryMode(DeviceVendor vendor) {
        switch (vendor) {
            case JET_FILE_II_STANDARD:
            case NOVA_STAR_STANDARD:
                return "UDP_BROADCAST";
            case NOVA_STAR_VIPLEX_CORE:
                return "SDK_DIRECT";
            case COLOR_LIGHT_STANDARD:
                return "TCP_SUBNET_PROBE";
            default:
                return "UNKNOWN";
        }
    }

    private String authMode(DeviceVendor vendor) {
        switch (vendor) {
            case NOVA_STAR_VIPLEX_CORE:
                return "SDK_ACCOUNT";
            case COLOR_LIGHT_STANDARD:
                return "BASIC_AUTH";
            default:
                return "NONE";
        }
    }
}
