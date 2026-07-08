package com.gateway.device.core.controller;

import com.gateway.common.Result;
import com.gateway.device.core.service.DeviceProtocolMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceProtocolMetadataControllerTest {

    @InjectMocks
    private DeviceProtocolMetadataController controller;

    @Mock
    private DeviceProtocolMetadataService deviceProtocolMetadataService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "deviceProtocolMetadataService",
                deviceProtocolMetadataService);
    }

    @Test
    void metadata_withNullVendor_returnsSuccessResult() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("metadataVersion", "1.0");
        expected.put("source", "terminal-gateway");
        expected.put("matched", true);

        when(deviceProtocolMetadataService.metadata(null)).thenReturn(expected);

        Result<Map<String, Object>> result = controller.metadata(null);

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMsg());
        assertNotNull(result.getTimestamp());
        assertSame(expected, result.getData());
    }

    @Test
    void metadata_withVendor_returnsFilteredResult() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("metadataVersion", "1.0");
        expected.put("filter", "COLOR_LIGHT");

        when(deviceProtocolMetadataService.metadata("COLOR_LIGHT")).thenReturn(expected);

        Result<Map<String, Object>> result = controller.metadata("COLOR_LIGHT");

        assertEquals(200, result.getCode());
        assertEquals("COLOR_LIGHT", result.getData().get("filter"));
        verify(deviceProtocolMetadataService).metadata("COLOR_LIGHT");
    }

    @Test
    void metadata_serviceReturnsEmpty_returnsDataSuccessfully() {
        Map<String, Object> emptyMap = new LinkedHashMap<>();
        when(deviceProtocolMetadataService.metadata("ANY")).thenReturn(emptyMap);

        Result<Map<String, Object>> result = controller.metadata("ANY");

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void metadata_resultIncludesTimestamp() {
        when(deviceProtocolMetadataService.metadata(any())).thenReturn(new LinkedHashMap<>());

        long before = System.currentTimeMillis();
        Result<Map<String, Object>> result = metadataCall();
        long after = System.currentTimeMillis();

        assertNotNull(result.getTimestamp());
        assertTrue(result.getTimestamp() >= before && result.getTimestamp() <= after,
                "timestamp should roughly reflect call time");
    }

    @Test
    void metadata_controllerNeverReturnsNull() {
        when(deviceProtocolMetadataService.metadata(any())).thenReturn(null);

        Result<Map<String, Object>> result = controller.metadata(null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void metadata_blankVendor_passesBlankValueThrough() {
        when(deviceProtocolMetadataService.metadata("   ")).thenReturn(new LinkedHashMap<>());

        controller.metadata("   ");

        verify(deviceProtocolMetadataService).metadata("   ");
    }

    private Result<Map<String, Object>> metadataCall() {
        return controller.metadata(null);
    }
}
