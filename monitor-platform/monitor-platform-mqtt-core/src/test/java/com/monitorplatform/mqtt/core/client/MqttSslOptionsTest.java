package com.monitorplatform.mqtt.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqttSslOptionsTest {

    @Test
    void default_optionsDoNotRequireCustomContext() {
        MqttSslOptions opts = new MqttSslOptions();
        assertFalse(opts.hasTruststore());
        assertFalse(opts.hasKeystore());
        assertFalse(opts.requiresCustomSslContext());
        assertTrue(opts.isHostnameVerificationEnabled());
    }

    @Test
    void trustset_pathWithBlank_isNotConsideredPresent() {
        MqttSslOptions opts = new MqttSslOptions();
        opts.setTruststorePath("  ");
        assertFalse(opts.hasTruststore());
        assertFalse(opts.requiresCustomSslContext());
    }

    @Test
    void trustset_pathSet_requiresCustomContext() {
        MqttSslOptions opts = new MqttSslOptions();
        opts.setTruststorePath("/app/certs/ca.jks");
        opts.setTruststorePassword("changeit");
        assertTrue(opts.hasTruststore());
        assertFalse(opts.hasKeystore());
        assertTrue(opts.requiresCustomSslContext());
    }

    @Test
    void keystore_pathSet_requiresCustomContext() {
        MqttSslOptions opts = new MqttSslOptions();
        opts.setKeystorePath("/app/certs/client.p12");
        opts.setKeystorePassword("keypass");
        assertFalse(opts.hasTruststore());
        assertTrue(opts.hasKeystore());
        assertTrue(opts.requiresCustomSslContext());
    }

    @Test
    void hostnameVerification_canBeDisabled() {
        MqttSslOptions opts = new MqttSslOptions();
        opts.setHostnameVerificationEnabled(false);
        assertFalse(opts.isHostnameVerificationEnabled());
    }
}
