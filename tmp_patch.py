p = r'd:\project02\monitor-platform\monitor-platform-monolith\src\main\java\com\monitorplatform\forward\service\impl\PublishGatewayConfigServiceImpl.java'
with open(p, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if 'import com.monitorplatform.forward.service.PublishGatewayConfigService;' in line:
        new_lines.append('import com.monitorplatform.forward.config.MqttDispatchProperties;\n')
        new_lines.append('import com.monitorplatform.forward.service.MqttCommandPublishService;\n')
        new_lines.append('import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;\n')
        new_lines.append(line)
    elif 'private DeviceFeignClient deviceFeignClient;\n' == line:
        new_lines.append(line)
        new_lines.append('\n')
        new_lines.append('    @Autowired(required = false)\n')
        new_lines.append('    private MqttDispatchProperties mqttDispatchProperties;\n')
        new_lines.append('    @Autowired(required = false)\n')
        new_lines.append('    private MqttCommandPublishService mqttCommandPublishService;\n')
    else:
        new_lines.append(line)

content = ''.join(new_lines)
last_brace = content.rfind('}')
mqtt_block = '''

    // ===== MQTT dispatch =====
    private static final String DISPATCH_MODE_HTTP = "http";
    private static final String DISPATCH_MODE_MQTT = "mqtt";
    private static final String DISPATCH_MODE_DUAL = "dual";

    private boolean shouldUseMqtt() {
        return mqttDispatchProperties != null
                && (DISPATCH_MODE_MQTT.equalsIgnoreCase(mqttDispatchProperties.getMode())
                        || DISPATCH_MODE_DUAL.equalsIgnoreCase(mqttDispatchProperties.getMode()));
    }

    private boolean shouldUseHttp() {
        return mqttDispatchProperties == null
                || DISPATCH_MODE_HTTP.equalsIgnoreCase(mqttDispatchProperties.getMode())
                || DISPATCH_MODE_DUAL.equalsIgnoreCase(mqttDispatchProperties.getMode());
    }

    private boolean dispatchViaMqtt(String gatewayDeviceId, String command,
                                    java.util.List<MqttCommandMessage.Action> actions) {
        if (mqttCommandPublishService == null || !mqttCommandPublishService.isMqttEnabled()) {
            log.warn("[MQTT] MQTT not enabled, skip: gatewayDeviceId={}", gatewayDeviceId);
            return false;
        }
        try {
            com.monitorplatform.forward.entity.DeviceMqttCommand mqttCmd =
                    mqttCommandPublishService.publishCommand(gatewayDeviceId, command, null, actions);
            if (mqttCmd == null || !com.monitorplatform.forward.entity.DeviceMqttCommand.STATUS_PUBLISHED.equals(mqttCmd.getStatus())) {
                log.error("[MQTT] command publish failed: gatewayDeviceId={}, command={}", gatewayDeviceId, command);
                return false;
            }
            log.info("[MQTT] command published: gatewayDeviceId={}, command={}, messageId={}",
                    gatewayDeviceId, command, mqttCmd.getMessageId());
            if (shouldUseHttp()) return true;
            com.monitorplatform.forward.entity.DeviceMqttCommand finalCmd =
                    mqttCommandPublishService.waitForFinalStatus(mqttCmd.getId(),
                            mqttDispatchProperties.getCommandTimeoutSec());
            boolean ok = mqttCommandPublishService.isSuccess(finalCmd);
            if (!ok) log.error("[MQTT] command failed/timeout: messageId={}", mqttCmd.getMessageId());
            else log.info("[MQTT] command success: messageId={}", mqttCmd.getMessageId());
            return ok;
        } catch (Exception e) {
            log.error("[MQTT] dispatch error: gatewayDeviceId={}", gatewayDeviceId, e);
            return false;
        }
    }
'''
content = content[:last_brace] + mqtt_block + '\n}\n'
with open(p, 'w', encoding='utf-8') as f:
    f.write(content)
print('Patched successfully')
