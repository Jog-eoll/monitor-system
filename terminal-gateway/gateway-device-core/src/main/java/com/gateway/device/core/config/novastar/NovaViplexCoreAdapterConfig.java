package com.gateway.device.core.config.novastar;

import com.gateway.device.core.config.FontsProperties;
import com.gateway.device.protocol.adapter.novastar.viplexcore.*;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreAccount;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexTextPageBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.transport.sdk.novastar.viplexcore.ViplexCoreLibrary;
import com.gateway.device.transport.sdk.novastar.viplexcore.ViplexCoreLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * NovaViplexCore SDK 适配器 Spring Bean 装配。
 */
@Slf4j
@Configuration
public class NovaViplexCoreAdapterConfig {

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public ViplexCoreLibrary viplexCoreLibrary(NovaViplexCoreProperties props) {
        log.info("加载 ViplexCore SDK, sdkDir={}", props.getSdkDir());
        return ViplexCoreLibrary.load(props.getSdkDir());
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public ViplexCoreLifecycleManager viplexCoreLifecycleManager(
            ViplexCoreLibrary library, NovaViplexCoreProperties props,
            DeviceAuthStore deviceAuthManager) {
        ViplexCoreLifecycleManager mgr = new ViplexCoreLifecycleManager(
                library, props.getDataDir(),
                props.getCompany(), props.getPhone(), props.getEmail());
        mgr.setAuthStore(deviceAuthManager);
        return mgr;
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreTextProperties novaViplexCoreTextProperties() {
        return new NovaViplexCoreTextProperties();
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreTextStyle novaViplexCoreTextStyle(NovaViplexCoreTextProperties textProps) {
        NovaViplexCoreTextStyle textStyle = new NovaViplexCoreTextStyle();
        textStyle.setFlowFontFamily(textProps.getFlowFontFamily());
        textStyle.setRunFontFamily(textProps.getRunFontFamily());
        if (textProps.getFontSize() != null) textStyle.setFontSize(textProps.getFontSize());
        textStyle.setTextColor(textProps.getTextColor());
        textStyle.setRunBackground(textProps.getRunBackground());
        textStyle.setFlowBackground(textProps.getFlowBackground());
        textStyle.setLanguage(textProps.getLanguage());
        return textStyle;
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public ViplexProgramPipeline viplexProgramPipeline(
            ViplexCoreLifecycleManager lifecycleManager,
            NovaViplexCoreTextStyle textStyle) {
        ViplexTextPageBuilder textPageBuilder = new ViplexTextPageBuilder(textStyle);
        return new ViplexProgramPipeline(lifecycleManager, textPageBuilder, Duration.ofSeconds(10));
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreAdapter novaViplexCoreAdapter(
            ViplexCoreLifecycleManager lifecycleManager,
            NovaViplexCoreTextStyle textStyle,
            ViplexProgramPipeline pipeline,
            FontsProperties fontsProperties) {
        return new NovaViplexCoreAdapter(lifecycleManager, textStyle, pipeline,
                fontsProperties.getLocalPath());
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreDiscoveryProvider novaViplexCoreDiscoveryProvider(
            ViplexCoreLifecycleManager lifecycleManager, NovaViplexCoreProperties props) {
        List<ViplexCoreAccount> accounts = props.toViplexCoreAccounts();
        return new NovaViplexCoreDiscoveryProvider(lifecycleManager,
                accounts != null ? accounts : Collections.emptyList());
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreComplianceValidator novaViplexCoreComplianceValidator() {
        return new NovaViplexCoreComplianceValidator();
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreDeviceInfoEnricher novaViplexCoreDeviceInfoEnricher() {
        return new NovaViplexCoreDeviceInfoEnricher();
    }

    @Bean
    @ConditionalOnProperty(name = "device.novastar.viplexcore.sdk-dir")
    public NovaViplexCoreDeviceRegistrationProvider novaViplexCoreDeviceRegistrationProvider(
            ViplexCoreLifecycleManager lifecycleManager) {
        return new NovaViplexCoreDeviceRegistrationProvider(lifecycleManager);
    }
}
