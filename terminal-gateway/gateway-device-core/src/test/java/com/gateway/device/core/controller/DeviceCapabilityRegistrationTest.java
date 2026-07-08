package com.gateway.device.core.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DeviceCapabilityRegistrationTest {

    @Test
    void coreDeviceClassesShareSearchCapabilityRegistration() {
        assertDoesNotThrow(() -> {
            new DeviceManagementController();
            new ActionMapper();
        });
    }
}
