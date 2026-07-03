package com.gateway.device.core.orchestrator;

import com.gateway.device.core.controller.dto.StandardizedControlPackage;
import com.gateway.device.core.controller.dto.StandardizedPublishPackage;
import com.gateway.device.core.controller.TargetToSelectorMapper;
import com.gateway.device.core.controller.dto.BatchCommandRequestDTO;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceSelector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecureCommandSelectorTest {

    @Test
    void publishTargetPrefersIpWhenDeviceIdAndIpBothPresent() throws Exception {
        PublishPackageOrchestrator orchestrator =
                new PublishPackageOrchestrator(null, null, null, null, null, null);
        StandardizedPublishPackage.TargetRef target = new StandardizedPublishPackage.TargetRef();
        target.setDeviceId("SN-001");
        target.setIp("192.168.10.21");
        target.setVendorHint("JET_FILE_II");

        Method method = PublishPackageOrchestrator.class
                .getDeclaredMethod("buildSelector", StandardizedPublishPackage.TargetRef.class);
        method.setAccessible(true);

        DeviceSelector selector = (DeviceSelector) method.invoke(orchestrator, target);

        assertNull(selector.getDeviceIds());
        assertEquals(Collections.singleton("192.168.10.21"), selector.getIps());
        assertEquals(Collections.singleton(DeviceVendor.JET_FILE_II), selector.getVendors());
    }

    @Test
    void controlTargetPrefersIpWhenDeviceIdAndIpBothPresent() throws Exception {
        ControlCommandOrchestrator orchestrator =
                new ControlCommandOrchestrator(null, null, null, null, null);
        StandardizedControlPackage.TargetInfo target = new StandardizedControlPackage.TargetInfo();
        target.setDeviceId("SN-002");
        target.setIp("192.168.10.22");
        target.setVendorHint("QINGSONG");

        Method method = ControlCommandOrchestrator.class
                .getDeclaredMethod("buildSelector", StandardizedControlPackage.TargetInfo.class, DeviceCapability.class);
        method.setAccessible(true);

        DeviceSelector selector = (DeviceSelector) method.invoke(orchestrator, target, CommonDeviceCapability.BRIGHTNESS_SET);

        assertNull(selector.getDeviceIds());
        assertEquals(Collections.singleton("192.168.10.22"), selector.getIps());
        assertEquals(Collections.singleton(CommonDeviceCapability.BRIGHTNESS_SET), selector.getRequiredCapabilities());
        assertEquals(Collections.singleton(DeviceVendor.JET_FILE_II), selector.getVendors());
    }

    @Test
    void publishTargetUsesDeviceIdWhenIpMissing() throws Exception {
        PublishPackageOrchestrator orchestrator =
                new PublishPackageOrchestrator(null, null, null, null, null, null);
        StandardizedPublishPackage.TargetRef target = new StandardizedPublishPackage.TargetRef();
        target.setDeviceId("SN-001");
        target.setVendorHint("JET_FILE_II");

        Method method = PublishPackageOrchestrator.class
                .getDeclaredMethod("buildSelector", StandardizedPublishPackage.TargetRef.class);
        method.setAccessible(true);

        DeviceSelector selector = (DeviceSelector) method.invoke(orchestrator, target);

        assertEquals(Collections.singleton("SN-001"), selector.getDeviceIds());
        assertNull(selector.getIps());
        assertEquals(Collections.singleton(DeviceVendor.JET_FILE_II), selector.getVendors());
    }

    @Test
    void controlTargetUsesDeviceIdWhenIpMissing() throws Exception {
        ControlCommandOrchestrator orchestrator =
                new ControlCommandOrchestrator(null, null, null, null, null);
        StandardizedControlPackage.TargetInfo target = new StandardizedControlPackage.TargetInfo();
        target.setDeviceId("SN-002");
        target.setVendorHint("QINGSONG");

        Method method = ControlCommandOrchestrator.class
                .getDeclaredMethod("buildSelector", StandardizedControlPackage.TargetInfo.class, DeviceCapability.class);
        method.setAccessible(true);

        DeviceSelector selector = (DeviceSelector) method.invoke(orchestrator, target, CommonDeviceCapability.BRIGHTNESS_SET);

        assertEquals(Collections.singleton("SN-002"), selector.getDeviceIds());
        assertNull(selector.getIps());
        assertEquals(Collections.singleton(CommonDeviceCapability.BRIGHTNESS_SET), selector.getRequiredCapabilities());
        assertEquals(Collections.singleton(DeviceVendor.JET_FILE_II), selector.getVendors());
    }

    @Test
    void batchTargetMapperMapsIpOnlyTargetToIpSelector() {
        TargetToSelectorMapper mapper = new TargetToSelectorMapper();
        BatchCommandRequestDTO.TargetRef target = new BatchCommandRequestDTO.TargetRef();
        target.setIp("192.168.10.23");
        target.setVendor("JET_FILE_II");

        DeviceSelector selector = mapper.map(target);

        assertNull(selector.getDeviceIds());
        assertEquals(Collections.singleton("192.168.10.23"), selector.getIps());
        assertEquals(Collections.singleton(DeviceVendor.JET_FILE_II), selector.getVendors());
        assertEquals(Boolean.TRUE, selector.getOnlineOnly());
    }

    @Test
    void batchTargetMapperPrefersIpWhenDeviceIdAndIpBothPresent() {
        TargetToSelectorMapper mapper = new TargetToSelectorMapper();
        BatchCommandRequestDTO.TargetRef target = new BatchCommandRequestDTO.TargetRef();
        target.setDeviceId("00-1D-6F-02-A1-89");
        target.setIp("192.168.10.23");
        target.setVendor("JET_FILE_II");

        DeviceSelector selector = mapper.map(target);

        assertNull(selector.getDeviceIds());
        assertEquals(Collections.singleton("192.168.10.23"), selector.getIps());
        assertEquals(Collections.singleton(DeviceVendor.JET_FILE_II), selector.getVendors());
        assertEquals(Boolean.TRUE, selector.getOnlineOnly());
    }
}
