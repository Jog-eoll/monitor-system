package com.gateway.device.core.controller;

import com.gateway.common.Result;
import com.gateway.device.core.service.AutoDiscoveryService;
import com.gateway.device.core.service.DeviceManagementService;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.RegistrationSource;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import com.gateway.device.protocol.model.discovery.ExplicitIpDiscoveredDevice;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeviceManagementControllerTest {

    @Test
    void register_jetFileIIOnlineRequestUsesProtocolRegistration() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);
        DeviceContext registered = DeviceContext.builder()
                .deviceId("QS-88")
                .ip("192.168.113.88")
                .port(9520)
                .vendor(DeviceVendor.JET_FILE_II_STANDARD)
                .online(true)
                .loggedIn(true)
                .capabilities(new LinkedHashSet<>(Collections.singleton(CommonDeviceCapability.PLAYLIST_SET)))
                .attributes(Collections.singletonMap("source", "protocol-registration"))
                .build();
        when(autoDiscoveryService.registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), eq(RegistrationSource.MANUAL_IP))).thenReturn(registered);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("JETFILEII", "192.168.113.88", 9520, null));

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("QS-88", result.getData().getDeviceId());
        assertTrue(result.getData().isOnline());
        assertTrue(result.getData().isLoggedIn());
        verify(deviceManagementService, never()).register(any(DeviceContext.class));

        ArgumentCaptor<DiscoveredDevice> discoveredCaptor = ArgumentCaptor.forClass(DiscoveredDevice.class);
        ArgumentCaptor<DeviceVendorMapping> mappingCaptor = ArgumentCaptor.forClass(DeviceVendorMapping.class);
        verify(autoDiscoveryService).registerDevice(discoveredCaptor.capture(), mappingCaptor.capture(),
                eq(RegistrationSource.MANUAL_IP));
        assertTrue(discoveredCaptor.getValue() instanceof ExplicitIpDiscoveredDevice);
        assertEquals("192.168.113.88", discoveredCaptor.getValue().getIp());
        assertEquals(9520, discoveredCaptor.getValue().getSourcePort());
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, mappingCaptor.getValue().getVendor());
        assertEquals(9520, mappingCaptor.getValue().effectivePort());
    }

    @Test
    void register_jetFileIIFailureDoesNotFallbackToMemoryRegister() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);
        when(autoDiscoveryService.registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), eq(RegistrationSource.MANUAL_IP))).thenReturn(null);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("QINGSONG", "192.168.113.88", 9520, true));

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("JetFileII"));
        assertNull(result.getData());
        verify(deviceManagementService, never()).register(any(DeviceContext.class));
    }

    @Test
    void register_jetFileIIOfflineRequestKeepsLegacyRegistration() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("JETFILEII", "192.168.113.88", 9520, false));

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertFalse(result.getData().isOnline());
        assertFalse(result.getData().isLoggedIn());
        verify(autoDiscoveryService, never()).registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), any(RegistrationSource.class));

        ArgumentCaptor<DeviceContext> deviceCaptor = ArgumentCaptor.forClass(DeviceContext.class);
        verify(deviceManagementService).register(deviceCaptor.capture());
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, deviceCaptor.getValue().getVendor());
        assertFalse(deviceCaptor.getValue().isOnline());
        assertFalse(deviceCaptor.getValue().isLoggedIn());
    }

    @Test
    void register_colorLightOnlineRequestUsesProtocolRegistration() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);
        DeviceContext registered = DeviceContext.builder()
                .deviceId("CL-90")
                .ip("192.168.113.90")
                .port(8989)
                .vendor(DeviceVendor.COLOR_LIGHT_STANDARD)
                .online(true)
                .loggedIn(true)
                .capabilities(new LinkedHashSet<>(Collections.singleton(CommonDeviceCapability.PLAYLIST_SET)))
                .attributes(Collections.singletonMap("source", "protocol-registration"))
                .build();
        when(autoDiscoveryService.registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), eq(RegistrationSource.MANUAL_IP))).thenReturn(registered);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("COLORLIGHT", "192.168.113.90", 8989, true));

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("CL-90", result.getData().getDeviceId());
        assertEquals("COLOR_LIGHT_STANDARD", result.getData().getVendor());
        assertTrue(result.getData().isOnline());
        assertTrue(result.getData().isLoggedIn());
        verify(deviceManagementService, never()).register(any(DeviceContext.class));

        ArgumentCaptor<DiscoveredDevice> discoveredCaptor = ArgumentCaptor.forClass(DiscoveredDevice.class);
        ArgumentCaptor<DeviceVendorMapping> mappingCaptor = ArgumentCaptor.forClass(DeviceVendorMapping.class);
        verify(autoDiscoveryService).registerDevice(discoveredCaptor.capture(), mappingCaptor.capture(),
                eq(RegistrationSource.MANUAL_IP));
        assertTrue(discoveredCaptor.getValue() instanceof ExplicitIpDiscoveredDevice);
        assertEquals("192.168.113.90", discoveredCaptor.getValue().getIp());
        assertEquals(8989, discoveredCaptor.getValue().getSourcePort());
        assertEquals(DeviceVendor.COLOR_LIGHT_STANDARD, mappingCaptor.getValue().getVendor());
        assertEquals(8989, mappingCaptor.getValue().effectivePort());
    }

    @Test
    void register_colorLightFailureDoesNotFallbackToMemoryRegister() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);
        when(autoDiscoveryService.registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), eq(RegistrationSource.MANUAL_IP))).thenReturn(null);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("COLORIGHT", "192.168.113.90", 8989, true));

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("ColorLight"));
        assertNull(result.getData());
        verify(deviceManagementService, never()).register(any(DeviceContext.class));
    }

    @Test
    void register_colorLightOfflineRequestKeepsLegacyRegistration() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("COLORLIGHT", "192.168.113.90", 8989, false));

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("COLOR_LIGHT_STANDARD", result.getData().getVendor());
        assertFalse(result.getData().isOnline());
        assertFalse(result.getData().isLoggedIn());
        verify(autoDiscoveryService, never()).registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), any(RegistrationSource.class));
        verify(deviceManagementService).register(any(DeviceContext.class));
    }

    @Test
    void register_novaStarOnlineRequestKeepsLegacyRegistration() {
        DeviceManagementService deviceManagementService = mock(DeviceManagementService.class);
        AutoDiscoveryService autoDiscoveryService = mock(AutoDiscoveryService.class);
        DeviceManagementController controller = controllerWith(deviceManagementService, autoDiscoveryService);

        Result<DeviceManagementController.DeviceView> result = controller.register(
                registerRequest("NOVA_STAR_VIPLEX_CORE", "192.168.113.91", 9520, true));

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("NOVA_STAR_VIPLEX_CORE", result.getData().getVendor());
        verify(autoDiscoveryService, never()).registerDevice(any(DiscoveredDevice.class),
                any(DeviceVendorMapping.class), any(RegistrationSource.class));
        verify(deviceManagementService).register(any(DeviceContext.class));
    }

    @Test
    void resolveVendor_qingsongAlias_returnsJetFileII() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "QINGSONG");
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, result);
    }

    @Test
    void resolveVendor_qingSongAlias_returnsJetFileII() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "Qing_Song");
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, result);
    }

    @Test
    void resolveVendor_jetFileIIAlias_returnsJetFileII() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "JetFileII");
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, result);
    }

    @Test
    void resolveVendor_colorlightAlias_returnsColorLight() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "COLORLIGHT");
        assertEquals(DeviceVendor.COLOR_LIGHT_STANDARD, result);
    }

    @Test
    void resolveVendor_colorLightAlias_returnsColorLight() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "Color_Light");
        assertEquals(DeviceVendor.COLOR_LIGHT_STANDARD, result);
    }

    @Test
    void resolveVendor_typoColoright_returnsColorLight() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "COLORIGHT");
        assertEquals(DeviceVendor.COLOR_LIGHT_STANDARD, result);
    }

    @Test
    void resolveVendor_novaStarViplexCore_returnsCorrectVendor() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "NOVA_STAR_VIPLEX_CORE");
        assertEquals(DeviceVendor.NOVA_STAR_VIPLEX_CORE, result);
    }

    @Test
    void resolveVendor_nullInput_returnsDefaultJetFileII() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, null);
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, result);
    }

    @Test
    void resolveVendor_emptyInput_returnsDefaultJetFileII() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "   ");
        assertEquals(DeviceVendor.JET_FILE_II_STANDARD, result);
    }

    @Test
    void resolveVendor_invalidVendor_returnsNull() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "UNKNOWN_VENDOR");
        assertNull(result);
    }

    @Test
    void resolveVendor_lowercaseInput_normalizesCorrectly() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "colorlight");
        assertEquals(DeviceVendor.COLOR_LIGHT_STANDARD, result);
    }

    @Test
    void resolveVendor_withDashes_normalizesCorrectly() {
        DeviceManagementController controller = new DeviceManagementController();

        DeviceVendor result = invokeResolveVendor(controller, "COLOR-LIGHT");
        assertEquals(DeviceVendor.COLOR_LIGHT_STANDARD, result);
    }

    @Test
    void defaultPort_colorlight_returns8989() {
        DeviceManagementController controller = new DeviceManagementController();

        int port = invokeDefaultPort(controller, DeviceVendor.COLOR_LIGHT_STANDARD);
        assertEquals(8989, port);
    }

    @Test
    void defaultPort_jetFileII_returns9520() {
        DeviceManagementController controller = new DeviceManagementController();

        int port = invokeDefaultPort(controller, DeviceVendor.JET_FILE_II_STANDARD);
        assertEquals(9520, port);
    }

    @Test
    void defaultPort_novaStar_returns9520() {
        DeviceManagementController controller = new DeviceManagementController();

        int port = invokeDefaultPort(controller, DeviceVendor.NOVA_STAR_VIPLEX_CORE);
        assertEquals(9520, port);
    }

    @Test
    void resolveCapabilities_jetFileII_returnsFullCapabilities() {
        DeviceManagementController controller = new DeviceManagementController();

        Set<DeviceCapability<?>> capabilities = invokeResolveCapabilities(
                controller, DeviceVendor.JET_FILE_II_STANDARD, null);

        assertNotNull(capabilities);
        assertFalse(capabilities.isEmpty());
        assertTrue(capabilities.contains(CommonDeviceCapability.BRIGHTNESS_SET));
        assertTrue(capabilities.contains(CommonDeviceCapability.SCREEN_BLACKOUT));
    }

    @Test
    void resolveCapabilities_colorlight_returnsFullCapabilities() {
        DeviceManagementController controller = new DeviceManagementController();

        Set<DeviceCapability<?>> capabilities = invokeResolveCapabilities(
                controller, DeviceVendor.COLOR_LIGHT_STANDARD, null);

        assertNotNull(capabilities);
        assertFalse(capabilities.isEmpty());
        assertTrue(capabilities.contains(CommonDeviceCapability.BRIGHTNESS_SET));
    }

    @Test
    void resolveCapabilities_novaViplexCore_returnsFullCapabilities() {
        DeviceManagementController controller = new DeviceManagementController();

        Set<DeviceCapability<?>> capabilities = invokeResolveCapabilities(
                controller, DeviceVendor.NOVA_STAR_VIPLEX_CORE, null);

        assertNotNull(capabilities);
        assertFalse(capabilities.isEmpty());
        assertTrue(capabilities.contains(CommonDeviceCapability.BRIGHTNESS_SET));
        assertTrue(capabilities.contains(CommonDeviceCapability.NTP_SET));
    }

    @Test
    void resolveCapabilities_withConfiguredCapabilities_returnsConfigured() {
        DeviceManagementController controller = new DeviceManagementController();

        Set<String> configured = new HashSet<>();
        configured.add("BRIGHTNESS_SET");

        Set<DeviceCapability<?>> capabilities = invokeResolveCapabilities(
                controller, DeviceVendor.COLOR_LIGHT_STANDARD, configured);

        assertNotNull(capabilities);
        assertEquals(1, capabilities.size());
        assertTrue(capabilities.contains(CommonDeviceCapability.BRIGHTNESS_SET));
    }

    @Test
    void resolveCapabilities_withInvalidCapabilities_skipsInvalid() {
        DeviceManagementController controller = new DeviceManagementController();

        Set<String> configured = new HashSet<>();
        configured.add("BRIGHTNESS_SET");
        configured.add("INVALID_CAPABILITY");

        Set<DeviceCapability<?>> capabilities = invokeResolveCapabilities(
                controller, DeviceVendor.COLOR_LIGHT_STANDARD, configured);

        assertNotNull(capabilities);
        assertEquals(1, capabilities.size());
        assertTrue(capabilities.contains(CommonDeviceCapability.BRIGHTNESS_SET));
    }

    @Test
    void resolveCapabilities_withConfiguredTakesPrecedenceOverDefault() {
        DeviceManagementController controller = new DeviceManagementController();

        Set<String> configured = new HashSet<>();
        configured.add("POWER_CONTROL_REBOOT");

        Set<DeviceCapability<?>> capabilities = invokeResolveCapabilities(
                controller, DeviceVendor.JET_FILE_II_STANDARD, configured);

        assertNotNull(capabilities);
        assertEquals(1, capabilities.size());
        assertTrue(capabilities.contains(CommonDeviceCapability.POWER_CONTROL_REBOOT));
    }

    @Test
    void firstNonBlank_returnsFirstNonBlankValue() {
        DeviceManagementController controller = new DeviceManagementController();

        String result = invokeFirstNonBlank(controller, "", "  ", "valid", "other");
        assertEquals("valid", result);
    }

    @Test
    void firstNonBlank_returnsNullWhenAllBlank() {
        DeviceManagementController controller = new DeviceManagementController();

        String result = invokeFirstNonBlank(controller, "", "  ", null);
        assertNull(result);
    }

    @Test
    void firstNonBlank_returnsNullWhenNullArray() {
        DeviceManagementController controller = new DeviceManagementController();

        String result = invokeFirstNonBlank(controller, (String[]) null);
        assertNull(result);
    }

    @Test
    void isBlank_null_returnsTrue() {
        DeviceManagementController controller = new DeviceManagementController();

        assertTrue(invokeIsBlank(controller, null));
    }

    @Test
    void isBlank_empty_returnsTrue() {
        DeviceManagementController controller = new DeviceManagementController();

        assertTrue(invokeIsBlank(controller, ""));
    }

    @Test
    void isBlank_whitespace_returnsTrue() {
        DeviceManagementController controller = new DeviceManagementController();

        assertTrue(invokeIsBlank(controller, "   "));
    }

    @Test
    void isBlank_nonBlank_returnsFalse() {
        DeviceManagementController controller = new DeviceManagementController();

        assertFalse(invokeIsBlank(controller, "value"));
    }

    private DeviceManagementController controllerWith(DeviceManagementService deviceManagementService,
                                                      AutoDiscoveryService autoDiscoveryService) {
        DeviceManagementController controller = new DeviceManagementController();
        ReflectionTestUtils.setField(controller, "deviceManagementService", deviceManagementService);
        ReflectionTestUtils.setField(controller, "autoDiscoveryService", autoDiscoveryService);
        return controller;
    }

    private DeviceManagementController.DeviceRegisterRequest registerRequest(String vendor, String ip,
                                                                             Integer port, Boolean online) {
        DeviceManagementController.DeviceRegisterRequest request =
                new DeviceManagementController.DeviceRegisterRequest();
        request.setVendor(vendor);
        request.setIp(ip);
        request.setPort(port);
        request.setOnline(online);
        return request;
    }

    private DeviceVendor invokeResolveVendor(DeviceManagementController controller, String vendor) {
        try {
            java.lang.reflect.Method method = DeviceManagementController.class.getDeclaredMethod("resolveVendor", String.class);
            method.setAccessible(true);
            return (DeviceVendor) method.invoke(controller, vendor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int invokeDefaultPort(DeviceManagementController controller, DeviceVendor vendor) {
        try {
            java.lang.reflect.Method method = DeviceManagementController.class.getDeclaredMethod("defaultPort", DeviceVendor.class);
            method.setAccessible(true);
            return (int) method.invoke(controller, vendor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<DeviceCapability<?>> invokeResolveCapabilities(
            DeviceManagementController controller, DeviceVendor vendor, Set<String> configured) {
        try {
            java.lang.reflect.Method method = DeviceManagementController.class.getDeclaredMethod(
                    "resolveCapabilities", DeviceVendor.class, Set.class);
            method.setAccessible(true);
            return (Set<DeviceCapability<?>>) method.invoke(controller, vendor, configured);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeFirstNonBlank(DeviceManagementController controller, String... values) {
        try {
            java.lang.reflect.Method method = DeviceManagementController.class.getDeclaredMethod("firstNonBlank", String[].class);
            method.setAccessible(true);
            return (String) method.invoke(controller, (Object) values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean invokeIsBlank(DeviceManagementController controller, String value) {
        try {
            java.lang.reflect.Method method = DeviceManagementController.class.getDeclaredMethod("isBlank", String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(controller, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
