package com.monitorplatform.device.controller;

import com.monitorplatform.device.entity.InfoBoardModelVendorMapping;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.InfoBoardManualAddDTO;
import com.monitorplatform.device.service.InfoBoardModelVendorMappingService;
import com.monitorplatform.device.service.UnifiedDeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedDeviceControllerTest {

    @InjectMocks
    private UnifiedDeviceController controller;

    @Mock
    private UnifiedDeviceService unifiedDeviceService;

    @Mock
    private InfoBoardModelVendorMappingService mappingService;

    @Mock
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<UnifiedDevice> deviceCaptor;

    @Value("${info-board.default-publish-gateway-url:http://127.0.0.1:8092}")
    private String defaultGatewayUrl;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "defaultPublishGatewayUrl",
                "http://127.0.0.1:8092");
    }

    @Test
    void updateDeviceIp_knownDevice_success() {
        when(unifiedDeviceService.updateDeviceIp("DEV-42", "10.0.0.5")).thenReturn(true);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateDeviceIp("DEV-42", "10.0.0.5");

        assertEquals(200, result.getCode());
    }

    @Test
    void updateDeviceIp_unknownDevice_returnsNotFound() {
        when(unifiedDeviceService.updateDeviceIp("UNKNOWN", "10.0.0.5")).thenReturn(false);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateDeviceIp("UNKNOWN", "10.0.0.5");

        assertEquals(400, result.getCode());
        assertNotNull(result.getMsg());
    }

    @Test
    void updateStatusByIp_foundDevice_returnsSuccess() {
        when(unifiedDeviceService.updateStatusByIp("192.168.1.10", "在线")).thenReturn(true);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateStatusByIp("192.168.1.10", "在线");

        assertEquals(200, result.getCode());
    }

    @Test
    void updateStatusByIp_unknownIp_returnsFailMessage() {
        when(unifiedDeviceService.updateStatusByIp("10.99.99.99", "离线")).thenReturn(false);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateStatusByIp("10.99.99.99", "离线");

        assertEquals(400, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void addInfoBoardManual_unsupportedModelCode_returnsError() {
        InfoBoardManualAddDTO dto = new InfoBoardManualAddDTO();
        dto.setModelCode("UNKNOWN-MODEL-XYZ");
        dto.setDeviceName("测试情报板");
        dto.setIpAddress("192.168.1.50");
        dto.setPort(8989);

        when(mappingService.findByModelCode("UNKNOWN-MODEL-XYZ")).thenReturn(Optional.empty());

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.addInfoBoardManual(dto);

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().toString().contains("不支持的型号"));
        verify(unifiedDeviceService, never()).addDevice(any());
    }

    @Test
    void addInfoBoardManual_unknownModel_savesDeviceAndReturnsGatewayResult() {
        InfoBoardManualAddDTO dto = new InfoBoardManualAddDTO();
        dto.setModelCode("CL-X5");
        dto.setDeviceName("测试情报板");
        dto.setIpAddress("192.168.1.50");
        dto.setPort(8989);
        dto.setLongitude(113.2644);
        dto.setLatitude(23.1291);
        dto.setPublishGatewayUrl("http://gw-1.local:8092");

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setDefaultPort(8989);
        mapping.setPlatformManufacturer("ColorLight");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));
        when(unifiedDeviceService.addDevice(any(UnifiedDevice.class))).thenReturn(true);

        Map<String, Object> gatewayResp = new HashMap<>();
        gatewayResp.put("code", 200);
        gatewayResp.put("msg", "Registered");
        when(restTemplate.postForObject(eq("http://gw-1.local:8092/api/terminal-devices/register"),
                any(Map.class), eq(Map.class))).thenReturn(gatewayResp);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.addInfoBoardManual(dto);

        assertEquals(200, result.getCode());
        verify(unifiedDeviceService).addDevice(deviceCaptor.capture());
        UnifiedDevice captured = deviceCaptor.getValue();
        assertEquals("测试情报板", captured.getDeviceName());
        assertEquals("info_board", captured.getDeviceType());
        assertEquals("192.168.1.50", captured.getIpAddress());
        assertEquals(Integer.valueOf(8989), captured.getPort());
        assertEquals("CL-X5", captured.getModel());
        assertEquals("ColorLight", captured.getManufacturer());
        assertEquals("离线", captured.getStatus());

        Map<String, Object> data = result.getData();
        assertNotNull(data.get("gatewayRegister"));
    }

    @Test
    void addInfoBoardManual_gatewayCallFailed_returnsDataWithGatewayFailureStatus() {
        InfoBoardManualAddDTO dto = new InfoBoardManualAddDTO();
        dto.setModelCode("CL-X5");
        dto.setDeviceName("测试情报板");
        dto.setIpAddress("192.168.1.50");

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setDefaultPort(8989);
        mapping.setPlatformManufacturer("ColorLight");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));
        when(unifiedDeviceService.addDevice(any(UnifiedDevice.class))).thenReturn(true);

        Map<String, Object> gatewayResp = new HashMap<>();
        gatewayResp.put("code", 500);
        gatewayResp.put("msg", "Internal error");
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class))).thenReturn(gatewayResp);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.addInfoBoardManual(dto);

        assertEquals(200, result.getCode());
        verify(unifiedDeviceService).addDevice(deviceCaptor.capture());
        verify(unifiedDeviceService).updateExtraInfo(eq(deviceCaptor.getValue().getDeviceId()), anyString());

        @SuppressWarnings("unchecked")
        Map<String, Object> gwReg = (Map<String, Object>) result.getData().get("gatewayRegister");
        assertEquals("FAILED", gwReg.get("status"));
    }

    @Test
    void addInfoBoardManual_gatewayThrowsRuntimeException_deviceStillSaved() {
        InfoBoardManualAddDTO dto = new InfoBoardManualAddDTO();
        dto.setModelCode("CL-X5");
        dto.setDeviceName("测试情报板");
        dto.setIpAddress("192.168.1.50");

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setDefaultPort(8989);
        mapping.setPlatformManufacturer("ColorLight");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));
        when(unifiedDeviceService.addDevice(any(UnifiedDevice.class))).thenReturn(true);
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.addInfoBoardManual(dto);

        assertEquals(200, result.getCode());
        verify(unifiedDeviceService).addDevice(any());
        @SuppressWarnings("unchecked")
        Map<String, Object> gwReg = (Map<String, Object>) result.getData().get("gatewayRegister");
        assertEquals("FAILED", gwReg.get("status"));
        assertTrue(gwReg.get("message").toString().contains("Connection refused"));
    }

    @Test
    void addInfoBoardManual_deviceSaveFailed_doesNotCallGateway() {
        InfoBoardManualAddDTO dto = new InfoBoardManualAddDTO();
        dto.setModelCode("CL-X5");
        dto.setDeviceName("测试情报板");
        dto.setIpAddress("192.168.1.50");

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setDefaultPort(8989);
        mapping.setPlatformManufacturer("ColorLight");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));
        when(unifiedDeviceService.addDevice(any(UnifiedDevice.class))).thenReturn(false);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.addInfoBoardManual(dto);

        assertEquals(400, result.getCode());
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void addInfoBoardManual_generatesDeviceIdWhenNotSupplied() {
        InfoBoardManualAddDTO dto = new InfoBoardManualAddDTO();
        dto.setModelCode("CL-X5");
        dto.setDeviceName("测试情报板");
        dto.setIpAddress("192.168.1.50");

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setDefaultPort(8989);
        mapping.setPlatformManufacturer("ColorLight");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));
        when(unifiedDeviceService.addDevice(any(UnifiedDevice.class))).thenReturn(true);

        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class))).thenReturn(null);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.addInfoBoardManual(dto);

        verify(unifiedDeviceService).addDevice(deviceCaptor.capture());
        String savedDeviceId = deviceCaptor.getValue().getDeviceId();
        assertNotNull(savedDeviceId);
        assertTrue(savedDeviceId.startsWith("IB-"));
    }

    @Test
    void retryGatewayRegister_nonInfoBoardDevice_returnsError() {
        UnifiedDevice device = new UnifiedDevice();
        device.setDeviceId("DEV-OTHER");
        device.setDeviceType("camera");

        when(unifiedDeviceService.findByDeviceId("DEV-OTHER")).thenReturn(device);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.retryGatewayRegister("DEV-OTHER");

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().toString().contains("情报板设备不存在"));
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void retryGatewayRegister_withExtraInfoPreservesTerminalGatewayUrl() {
        UnifiedDevice device = new UnifiedDevice();
        device.setDeviceId("IB-001");
        device.setDeviceType("info_board");
        device.setModel("CL-X5");
        device.setIpAddress("192.168.1.50");
        device.setPort(8989);

        Map<String, Object> gatewayReg = new HashMap<>();
        gatewayReg.put("terminalGatewayUrl", "http://tgw-backup.local:8093");
        Map<String, Object> extraInfoMap = new HashMap<>();
        extraInfoMap.put("gatewayRegister", gatewayReg);
        try {
            device.setExtraInfo(new ObjectMapper().writeValueAsString(extraInfoMap));
        } catch (Exception e) {
            fail("Failed to build extraInfo JSON");
        }

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(unifiedDeviceService.findByDeviceId("IB-001")).thenReturn(device);
        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));

        Map<String, Object> gatewayResp = new HashMap<>();
        gatewayResp.put("code", 200);
        gatewayResp.put("msg", "OK");
        when(restTemplate.postForObject(eq("http://127.0.0.1:8092/api/terminal-devices/register"),
                any(Map.class), eq(Map.class))).thenReturn(gatewayResp);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.retryGatewayRegister("IB-001");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(anyString(), bodyCaptor.capture(), any());
        Map<String, Object> capturedBody = bodyCaptor.getValue();
        assertEquals("http://tgw-backup.local:8093", capturedBody.get("terminalGatewayUrl"));

        @SuppressWarnings("unchecked")
        Map<String, Object> gwReg = (Map<String, Object>) result.getData().get("gatewayRegister");
        assertEquals("http://tgw-backup.local:8093", gwReg.get("terminalGatewayUrl"));
    }

    @Test
    void retryGatewayRegister_withoutMappingUsesFallbackVendor() {
        UnifiedDevice device = new UnifiedDevice();
        device.setDeviceId("IB-002");
        device.setDeviceType("info_board");
        device.setModel("UNKNOWN-MOD");
        device.setIpAddress("192.168.1.51");
        device.setPort(8989);

        when(unifiedDeviceService.findByDeviceId("IB-002")).thenReturn(device);
        when(mappingService.findByModelCode("UNKNOWN-MOD")).thenReturn(Optional.empty());

        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class))).thenReturn(null);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.retryGatewayRegister("IB-002");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(anyString(), bodyCaptor.capture(), any());
        assertEquals("COLORLIGHT", bodyCaptor.getValue().get("vendor"));

        @SuppressWarnings("unchecked")
        Map<String, Object> gwReg = (Map<String, Object>) result.getData().get("gatewayRegister");
        assertEquals("FAILED", gwReg.get("status"));
    }

    @Test
    void retryGatewayRegister_successReturnsSuccessMessageCode() {
        UnifiedDevice device = new UnifiedDevice();
        device.setDeviceId("IB-003");
        device.setDeviceType("info_board");
        device.setModel("CL-X5");
        device.setIpAddress("192.168.1.52");
        device.setPort(8989);

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(unifiedDeviceService.findByDeviceId("IB-003")).thenReturn(device);
        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));

        Map<String, Object> gwResp = new HashMap<>();
        gwResp.put("code", 200);
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class))).thenReturn(gwResp);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.retryGatewayRegister("IB-003");

        assertEquals(200, result.getCode());
        assertEquals("重试注册成功", result.getMsg());
    }

    @Test
    void retryGatewayRegister_failedRegistrationReturnsDefaultCode() {
        UnifiedDevice device = new UnifiedDevice();
        device.setDeviceId("IB-004");
        device.setDeviceType("info_board");
        device.setModel("CL-X5");
        device.setIpAddress("192.168.1.53");
        device.setPort(8989);

        InfoBoardModelVendorMapping mapping = new InfoBoardModelVendorMapping();
        mapping.setModelCode("CL-X5");
        mapping.setTerminalVendorHint("COLORLIGHT");

        when(unifiedDeviceService.findByDeviceId("IB-004")).thenReturn(device);
        when(mappingService.findByModelCode("CL-X5")).thenReturn(Optional.of(mapping));

        Map<String, Object> gwResp = new HashMap<>();
        gwResp.put("code", 500);
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(Map.class))).thenReturn(gwResp);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.retryGatewayRegister("IB-004");

        assertEquals(200, result.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> gwReg = (Map<String, Object>) result.getData().get("gatewayRegister");
        assertEquals("FAILED", gwReg.get("status"));
    }

    @Test
    void updateLocation_validCoordinates_returnsSuccess() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", "IB-001");
        body.put("longitude", 113.2644);
        body.put("latitude", 23.1291);

        when(unifiedDeviceService.updateDeviceLocation("IB-001", 113.2644, 23.1291)).thenReturn(true);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateLocation(body);

        assertEquals(200, result.getCode());
    }

    @Test
    void updateLocation_missingDeviceId_returnsParameterError() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("longitude", 113.2644);
        body.put("latitude", 23.1291);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateLocation(body);

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().toString().contains("参数缺失"));
    }

    @Test
    void updateLocation_nonNumericLongitude_returnsFormatError() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", "IB-001");
        body.put("longitude", "not-a-number");
        body.put("latitude", 23.1291);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateLocation(body);

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().toString().contains("参数格式错误"));
    }

    @Test
    void updateLocation_numericStringCoordinates_parsedSuccessfully() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", "IB-001");
        body.put("longitude", "113.2644");
        body.put("latitude", "23.1291");

        when(unifiedDeviceService.updateDeviceLocation("IB-001", 113.2644, 23.1291)).thenReturn(true);

        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.updateLocation(body);

        assertEquals(200, result.getCode());
        verify(unifiedDeviceService).updateDeviceLocation("IB-001", 113.2644, 23.1291);
    }

    @Test
    void batchUpdateStatusByProbe_nullInput_returnsDataNull() {
        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.batchUpdateStatusByProbe(null);

        assertEquals(200, result.getCode());
        assertNull(result.getData());
        verify(unifiedDeviceService, never()).updateStatusByIpSkipAlarm(anyString(), anyString());
    }

    @Test
    void batchUpdateStatusByProbe_emptyInput_returnsZeroTotals() {
        com.monitorplatform.common.entity.Result<?> result = (com.monitorplatform.common.entity.Result<?>) controller.batchUpdateStatusByProbe(new ArrayList<>());

        assertEquals(200, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void batchUpdateStatusByProbe_reachableTrue_marksOnlineAndSkipsAlarm() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ip", "192.168.1.50");
        item.put("port", 8989);
        item.put("reachable", true);
        input.add(item);

        when(unifiedDeviceService.updateStatusByIpSkipAlarm("192.168.1.50", "在线")).thenReturn(true);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.batchUpdateStatusByProbe(input);

        verify(unifiedDeviceService).updateStatusByIpSkipAlarm("192.168.1.50", "在线");
        assertEquals(1, (int) (Integer) result.getData().get("updated"));
        assertEquals(0, result.getData().get("skipped"));
    }

    @Test
    void batchUpdateStatusByProbe_statusSkippedForAlarmDevice_incrementsSkip() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ip", "192.168.1.50");
        item.put("reachable", true);
        input.add(item);

        when(unifiedDeviceService.updateStatusByIpSkipAlarm("192.168.1.50", "在线")).thenReturn(false);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.batchUpdateStatusByProbe(input);

        assertEquals(0, result.getData().get("updated"));
        assertEquals(1, result.getData().get("skipped"));
    }

    @Test
    void batchUpdateStatusByProbe_reachableFalse_marksOffline() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ip", "192.168.1.50");
        item.put("reachable", false);
        input.add(item);

        when(unifiedDeviceService.updateStatusByIpSkipAlarm("192.168.1.50", "离线")).thenReturn(true);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.batchUpdateStatusByProbe(input);

        verify(unifiedDeviceService).updateStatusByIpSkipAlarm("192.168.1.50", "离线");
        assertEquals(1, result.getData().get("updated"));
    }

    @Test
    void batchUpdateStatusByProbe_missingIp_skipsEntry() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reachable", true);
        input.add(item);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.batchUpdateStatusByProbe(input);

        verify(unifiedDeviceService, never()).updateStatusByIpSkipAlarm(anyString(), anyString());
        assertEquals(1, result.getData().get("total"));
        assertEquals(0, result.getData().get("updated"));
    }

    @Test
    void batchUpdateStatusByProbe_multipleResults_aggregatesCounts() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(probeResult("10.0.0.1", true));
        input.add(probeResult("10.0.0.2", false));
        input.add(probeResult("10.0.0.3", true));

        when(unifiedDeviceService.updateStatusByIpSkipAlarm("10.0.0.1", "在线")).thenReturn(true);
        when(unifiedDeviceService.updateStatusByIpSkipAlarm("10.0.0.2", "离线")).thenReturn(true);
        when(unifiedDeviceService.updateStatusByIpSkipAlarm("10.0.0.3", "在线")).thenReturn(false);

        @SuppressWarnings("unchecked")
        com.monitorplatform.common.entity.Result<Map<String, Object>> result = (com.monitorplatform.common.entity.Result<Map<String, Object>>) controller.batchUpdateStatusByProbe(input);

        assertEquals(3, result.getData().get("total"));
        assertEquals(2, result.getData().get("updated"));
        assertEquals(1, result.getData().get("skipped"));
    }

    private static Map<String, Object> probeResult(String ip, boolean reachable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ip", ip);
        result.put("reachable", reachable);
        return result;
    }
}
