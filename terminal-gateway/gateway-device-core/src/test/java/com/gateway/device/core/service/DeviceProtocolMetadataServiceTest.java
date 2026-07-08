package com.gateway.device.core.service;

import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceProtocolMetadataServiceTest {

    @Mock
    private ProtocolRouter protocolRouter;

    @Mock
    private VendorProtocolAdapter mockAdapter;

    private DeviceProtocolMetadataService service;

    @BeforeEach
    void setUp() {
        service = new DeviceProtocolMetadataService(protocolRouter);
    }

    @Test
    void metadata_noFilter_returnsAllVendors() {
        when(protocolRouter.findByVendor(any())).thenReturn(null);

        Map<String, Object> result = service.metadata(null);

        assertNotNull(result);
        assertEquals("1.0", result.get("metadataVersion"));
        assertEquals("terminal-gateway", result.get("source"));
        assertNotNull(result.get("generatedAt"));
        assertTrue((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertNotNull(vendors);
        assertEquals(4, vendors.size());
    }

    @Test
    void metadata_withFilter_returnsFilteredVendors() {
        when(protocolRouter.findByVendor(DeviceVendor.COLOR_LIGHT_STANDARD)).thenReturn(null);

        Map<String, Object> result = service.metadata("COLOR_LIGHT_STANDARD");

        assertNotNull(result);
        assertTrue((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertNotNull(vendors);
        assertEquals(1, vendors.size());
        assertEquals("COLOR_LIGHT_STANDARD", vendors.get(0).get("vendorCode"));
    }

    @Test
    void metadata_withInvalidFilter_returnsEmptyMatched() {
        Map<String, Object> result = service.metadata("INVALID_VENDOR");

        assertNotNull(result);
        assertFalse((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertNotNull(vendors);
        assertTrue(vendors.isEmpty());
    }

    @Test
    void metadata_withAliasFilter_returnsMatchingVendor() {
        when(protocolRouter.findByVendor(DeviceVendor.JET_FILE_II_STANDARD)).thenReturn(null);

        Map<String, Object> result = service.metadata("QINGSONG");

        assertNotNull(result);
        assertTrue((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertNotNull(vendors);
        assertEquals(1, vendors.size());
        assertEquals("JET_FILE_II_STANDARD", vendors.get(0).get("vendorCode"));
    }

    @Test
    void metadata_vendorContainsExpectedFields() {
        when(protocolRouter.findByVendor(any())).thenReturn(null);

        Map<String, Object> result = service.metadata("COLOR_LIGHT_STANDARD");

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        Map<String, Object> vendor = vendors.get(0);

        assertNotNull(vendor.get("vendorCode"));
        assertNotNull(vendor.get("vendorName"));
        assertNotNull(vendor.get("aliases"));
        assertNotNull(vendor.get("productCode"));
        assertNotNull(vendor.get("productName"));
        assertNotNull(vendor.get("defaultPort"));
        assertNotNull(vendor.get("transportType"));
        assertNotNull(vendor.get("discoveryMode"));
        assertNotNull(vendor.get("authMode"));
        assertNotNull(vendor.get("adapterAvailable"));
        assertNotNull(vendor.get("controlCommands"));
        assertNotNull(vendor.get("capabilities"));
    }

    @Test
    void metadata_withAdapter_returnsCapabilities() {
        Set<DeviceCapability<?>> capabilities = new LinkedHashSet<>();
        capabilities.add(CommonDeviceCapability.BRIGHTNESS_SET);
        capabilities.add(CommonDeviceCapability.SCREEN_BLACKOUT);

        when(protocolRouter.findByVendor(DeviceVendor.COLOR_LIGHT_STANDARD)).thenReturn(mockAdapter);
        when(mockAdapter.capabilities()).thenReturn(capabilities);
        when(mockAdapter.transportType()).thenReturn(null);

        Map<String, Object> result = service.metadata("COLOR_LIGHT_STANDARD");

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        Map<String, Object> vendor = vendors.get(0);

        assertTrue((Boolean) vendor.get("adapterAvailable"));

        List<Map<String, Object>> caps = (List<Map<String, Object>>) vendor.get("capabilities");
        assertNotNull(caps);
        assertEquals(2, caps.size());

        List<Map<String, Object>> commands = (List<Map<String, Object>>) vendor.get("controlCommands");
        assertNotNull(commands);
        assertFalse(commands.isEmpty());
    }

    @Test
    void metadata_withDashInVendor_normalizesCorrectly() {
        when(protocolRouter.findByVendor(DeviceVendor.JET_FILE_II_STANDARD)).thenReturn(null);

        Map<String, Object> result = service.metadata("JET-FILE-II");

        assertTrue((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertEquals(1, vendors.size());
    }

    @Test
    void metadata_withSpaceInVendor_normalizesCorrectly() {
        when(protocolRouter.findByVendor(DeviceVendor.COLOR_LIGHT_STANDARD)).thenReturn(null);

        Map<String, Object> result = service.metadata("color light");

        assertTrue((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertEquals(1, vendors.size());
    }

    @Test
    void metadata_withLowerCaseVendor_normalizesCorrectly() {
        when(protocolRouter.findByVendor(DeviceVendor.COLOR_LIGHT_STANDARD)).thenReturn(null);

        Map<String, Object> result = service.metadata("colorlight");

        assertTrue((Boolean) result.get("matched"));

        List<Map<String, Object>> vendors = (List<Map<String, Object>>) result.get("vendors");
        assertEquals(1, vendors.size());
    }

    @Test
    void metadata_filterFieldContainsOriginalValue() {
        Map<String, Object> result = service.metadata("COLOR_LIGHT_STANDARD");

        assertEquals("COLOR_LIGHT_STANDARD", result.get("filter"));
    }

    @Test
    void metadata_filterFieldIsNullWhenNoFilter() {
        Map<String, Object> result = service.metadata(null);

        assertNull(result.get("filter"));
    }

    @Test
    void metadata_filterFieldIsEmptyWhenBlankFilter() {
        Map<String, Object> result = service.metadata("   ");

        assertNull(result.get("filter"));
    }
}
