package com.monitorplatform.device.service.impl;

import com.monitorplatform.device.entity.InfoBoardModelVendorMapping;
import com.monitorplatform.device.mapper.InfoBoardModelVendorMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfoBoardModelVendorMappingServiceImplTest {

    @Mock
    private InfoBoardModelVendorMappingMapper mapper;

    @InjectMocks
    private InfoBoardModelVendorMappingServiceImpl service;

    private InfoBoardModelVendorMapping sampleMapping;

    @BeforeEach
    void setUp() {
        sampleMapping = new InfoBoardModelVendorMapping();
        sampleMapping.setId(1L);
        sampleMapping.setModelCode("A2K");
        sampleMapping.setModelName("A2K情报板");
        sampleMapping.setPlatformManufacturer("colorlight");
        sampleMapping.setTerminalVendorHint("COLORLIGHT");
        sampleMapping.setDefaultPort(8989);
        sampleMapping.setEnabled(true);
    }

    @Test
    void listAll_returnsOnlyEnabledMappings() {
        InfoBoardModelVendorMapping disabled = new InfoBoardModelVendorMapping();
        disabled.setModelCode("DISABLED");
        disabled.setEnabled(false);

        when(mapper.selectList(any())).thenReturn(Arrays.asList(sampleMapping));

        List<InfoBoardModelVendorMapping> result = service.listAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("A2K", result.get(0).getModelCode());
    }

    @Test
    void listAllIncludingDisabled_returnsAllMappings() {
        InfoBoardModelVendorMapping disabled = new InfoBoardModelVendorMapping();
        disabled.setModelCode("DISABLED");
        disabled.setEnabled(false);

        when(mapper.selectList(any())).thenReturn(Arrays.asList(sampleMapping, disabled));

        List<InfoBoardModelVendorMapping> result = service.listAllIncludingDisabled();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void findByModelCode_existingEnabledMapping_returnsMapping() {
        when(mapper.selectOne(any())).thenReturn(sampleMapping);

        Optional<InfoBoardModelVendorMapping> result = service.findByModelCode("A2K");

        assertTrue(result.isPresent());
        assertEquals("A2K", result.get().getModelCode());
        assertEquals("colorlight", result.get().getPlatformManufacturer());
    }

    @Test
    void findByModelCode_nonExisting_returnsEmpty() {
        when(mapper.selectOne(any())).thenReturn(null);

        Optional<InfoBoardModelVendorMapping> result = service.findByModelCode("NONEXISTENT");

        assertFalse(result.isPresent());
    }

    @Test
    void add_setsCreateTimeAndUpdateTime() {
        when(mapper.insert(any())).thenReturn(1);

        boolean result = service.add(sampleMapping);

        assertTrue(result);
        assertNotNull(sampleMapping.getCreateTime());
        assertNotNull(sampleMapping.getUpdateTime());
    }

    @Test
    void add_setsEnabledToTrueWhenNull() {
        sampleMapping.setEnabled(null);
        when(mapper.insert(any())).thenReturn(1);

        boolean result = service.add(sampleMapping);

        assertTrue(result);
        assertTrue(sampleMapping.getEnabled());
    }

    @Test
    void add_returnsFalseWhenInsertFails() {
        when(mapper.insert(any())).thenReturn(0);

        boolean result = service.add(sampleMapping);

        assertFalse(result);
    }

    @Test
    void update_setsUpdateTime() {
        when(mapper.updateById(any())).thenReturn(1);

        boolean result = service.update(sampleMapping);

        assertTrue(result);
        assertNotNull(sampleMapping.getUpdateTime());
    }

    @Test
    void update_returnsFalseWhenUpdateFails() {
        when(mapper.updateById(any())).thenReturn(0);

        boolean result = service.update(sampleMapping);

        assertFalse(result);
    }

    @Test
    void delete_returnsTrueWhenDeleteSucceeds() {
        when(mapper.deleteById(1L)).thenReturn(1);

        boolean result = service.delete(1L);

        assertTrue(result);
    }

    @Test
    void delete_returnsFalseWhenDeleteFails() {
        when(mapper.deleteById(999L)).thenReturn(0);

        boolean result = service.delete(999L);

        assertFalse(result);
    }

    @Test
    void getById_returnsMapping() {
        when(mapper.selectById(1L)).thenReturn(sampleMapping);

        InfoBoardModelVendorMapping result = service.getById(1L);

        assertNotNull(result);
        assertEquals("A2K", result.getModelCode());
    }

    @Test
    void getById_returnsNullWhenNotFound() {
        when(mapper.selectById(999L)).thenReturn(null);

        InfoBoardModelVendorMapping result = service.getById(999L);

        assertNull(result);
    }
}
