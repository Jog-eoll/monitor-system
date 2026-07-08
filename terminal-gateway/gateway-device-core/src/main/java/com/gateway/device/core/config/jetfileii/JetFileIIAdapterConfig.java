package com.gateway.device.core.config.jetfileii;

import com.gateway.device.core.config.FontsProperties;
import com.gateway.device.protocol.adapter.jetfileii.standard.JetFileIIAdapter;
import com.gateway.device.protocol.adapter.jetfileii.standard.JetFileIICredentialStore;
import com.gateway.device.protocol.adapter.jetfileii.standard.JetFileIIDeviceRegistrationProvider;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.text.NmgResources;
import com.gateway.device.protocol.base.jetfileii.standard.text.NmgTextStyle;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.FontColor;
import com.gateway.device.transport.netty.NettyTransportManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JetFileII 适配器 Spring Bean 装配。
 */
@Configuration
public class JetFileIIAdapterConfig {

    @Bean
    public JetFileIIAdapter jetFileIIAdapter(NettyTransportManager transportManager,
                                             JetFileIITextProperties props,
                                             FontsProperties fontsProperties,
                                             JetFileIIFileExtensionProperties extProps) {
        byte[] fontColorBytes;
        if (props.getFontColor() != null) {
            fontColorBytes = new byte[]{props.getFontColor().getCode()};
        } else if (props.getFontColorBgr() != null && props.getFontColorBgr().contains(",")) {
            String[] p = props.getFontColorBgr().split(",");
            if (p.length == 3) {
                fontColorBytes = NmgResources.customBgr(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()));
            } else {
                fontColorBytes = new byte[]{FontColor.RED.getCode()};
            }
        } else {
            fontColorBytes = new byte[]{FontColor.RED.getCode()};
        }
        NmgTextStyle style = new NmgTextStyle(
                props.getFont(),
                fontColorBytes,
                props.getEffectIn(),
                props.getEffectOut());
        return new JetFileIIAdapter(transportManager,
                props.resolveFileName(),
                style,
                fontsProperties.getLocalPath(),
                extProps);
    }

    @Bean
    public JetFileIICredentialStore jetFileIICredentialStore(JetFileIIProperties props) {
        return new JetFileIICredentialStore(props.toJetFileIIAccounts());
    }

    @Bean
    public JetFileIIDeviceRegistrationProvider jetFileIIDeviceRegistrationProvider(
            NettyTransportManager transportManager,
            JetFileIICredentialStore credentialStore,
            DeviceAuthStore deviceAuthManager) {
        JetFileIIMessaging messaging = new JetFileIIMessaging();
        JetFileIIDeviceRegistrationProvider provider = new JetFileIIDeviceRegistrationProvider(
                messaging, transportManager, credentialStore);
        provider.setAuthStore(deviceAuthManager);
        return provider;
    }
}
