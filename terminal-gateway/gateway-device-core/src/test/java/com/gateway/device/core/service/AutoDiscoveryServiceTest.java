package com.gateway.device.core.service;

import com.gateway.device.core.config.DiscoveryProperties;
import com.gateway.device.core.executor.DeviceCommandExecutor;
import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.core.store.DeviceRegistryManager;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.transport.netty.NettyTransportManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.util.Collections;

import static org.mockito.Mockito.*;

class AutoDiscoveryServiceTest {

    @Test
    void scan_defaultMarkOfflineWhenMissingFalseDoesNotMarkExistingDeviceOffline() {
        DiscoveryProperties properties = new DiscoveryProperties();
        properties.setMappings(Collections.emptyList());
        DeviceRegistryManager deviceRegistry = mock(DeviceRegistryManager.class);
        DeviceContext existing = DeviceContext.builder()
                .deviceId("QS-88")
                .ip("192.168.113.88")
                .port(9520)
                .vendor(DeviceVendor.JET_FILE_II_STANDARD)
                .online(true)
                .loggedIn(true)
                .build();
        when(deviceRegistry.list()).thenReturn(Collections.singletonList(existing));

        AutoDiscoveryService service = new AutoDiscoveryService(
                properties,
                deviceRegistry,
                mock(ProtocolRouter.class),
                mock(TaskScheduler.class),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                mock(NettyTransportManager.class),
                mock(ApplicationEventPublisher.class),
                mock(DeviceCommandExecutor.class),
                mock(DeviceAuthStore.class));

        service.scan();

        verify(deviceRegistry, never()).markOffline(anyString());
    }
}
