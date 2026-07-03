package com.gateway.device.core.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.device.core.controller.dto.SecureGatewayEnvelope;
import com.gateway.device.core.controller.dto.StandardizedControlPackage;
import com.gateway.device.core.controller.dto.StandardizedPublishPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 密文信封解析器 —— 兼容新信封与旧扁平 DTO。
 * <p>
 * 解密后的明文 JSON 存在两种形态：
 * <ol>
 *   <li>新契约：包含 {@code schemaVersion} / {@code messageType}，使用 {@link SecureGatewayEnvelope}
 *       外层包装，业务内容在 {@code publish} / {@code control} 子节点中。</li>
 *   <li>旧契约：扁平结构，直接反序列化为 {@link StandardizedPublishPackage}
 *       或 {@link StandardizedControlPackage}。</li>
 * </ol>
 * 本组件通过探测根节点是否存在信封标记来选择解析路径，保证向后兼容。
 * </p>
 */
@Slf4j
@Component
public class SecureEnvelopeParser {

    private final ObjectMapper objectMapper;

    public SecureEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * 解析发布包 JSON 为标准发布包。
     * <p>若存在信封标记且 {@code messageType=PUBLISH}，从 {@code envelope.publish} 子节点提取
     * 并补齐信封顶层的 {@code deliveryTaskId} / {@code target} / {@code publishPermit}；
     * 否则按扁平发布包解析。</p>
     *
     * @return 解析结果，{@link ParseOutcome#isEnvelope()} 指示是否走了信封路径
     */
    public ParseOutcome<StandardizedPublishPackage> parsePublish(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        SecureGatewayEnvelope envelope = tryReadEnvelope(root);
        if (envelope != null && envelope.isPublish()) {
            StandardizedPublishPackage pkg = extractPublish(envelope);
            return ParseOutcome.envelope(pkg, envelope);
        }
        // 旧扁平结构
        StandardizedPublishPackage pkg = objectMapper.treeToValue(root, StandardizedPublishPackage.class);
        return ParseOutcome.legacy(pkg);
    }

    /**
     * 解析控制包 JSON 为标准控制包。
     * <p>若存在信封标记且 {@code messageType=CONTROL}，从 {@code envelope.control} 子节点提取
     * 并补齐信封顶层的 {@code taskId} / {@code target} / {@code source}；
     * 否则按扁平控制包解析。</p>
     */
    public ParseOutcome<StandardizedControlPackage> parseControl(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        SecureGatewayEnvelope envelope = tryReadEnvelope(root);
        if (envelope != null && envelope.isControl()) {
            StandardizedControlPackage pkg = extractControl(envelope);
            return ParseOutcome.envelope(pkg, envelope);
        }
        StandardizedControlPackage pkg = objectMapper.treeToValue(root, StandardizedControlPackage.class);
        return ParseOutcome.legacy(pkg);
    }

    /**
     * 探测根节点是否携带信封标记（仅读取必要字段，避免提前绑定全部结构）。
     */
    private SecureGatewayEnvelope tryReadEnvelope(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode schemaVersion = root.get("schemaVersion");
        JsonNode messageType = root.get("messageType");
        boolean hasMarker = (schemaVersion != null && !schemaVersion.isNull() && !schemaVersion.asText().isEmpty())
                || (messageType != null && !messageType.isNull() && !messageType.asText().isEmpty());
        if (!hasMarker) {
            return null;
        }
        try {
            return objectMapper.treeToValue(root, SecureGatewayEnvelope.class);
        } catch (Exception e) {
            log.warn("[信封解析] 信封标记存在但解析失败，回退扁平解析: {}", e.getMessage());
            return null;
        }
    }

    private StandardizedPublishPackage extractPublish(SecureGatewayEnvelope envelope) throws IOException {
        StandardizedPublishPackage pkg = new StandardizedPublishPackage();

        // 任务 ID：信封顶层 deliveryTaskId 优先
        pkg.setDeliveryTaskId(envelope.getDeliveryTaskId());
        // publishPermit：信封顶层优先，其次 publish 内层
        pkg.setPublishPermit(firstNonBlank(envelope.getPublishPermit(),
                envelope.getPublish() != null ? envelope.getPublish().getPublishPermit() : null));

        SecureGatewayEnvelope.PublishPayload publish = envelope.getPublish();
        if (publish != null) {
            pkg.setAction(publish.getAction());
            pkg.setPlaylist(publish.getPlaylist());
            pkg.setFiles(publish.getFiles());
            pkg.setOptions(publish.getOptions());
            pkg.setSigmaPublishId(publish.getSigmaPublishId());
        }

        // target：信封顶层覆盖 payload（信封作为权威来源）
        SecureGatewayEnvelope.TargetRef targetRef = envelope.getTarget();
        if (targetRef != null) {
            StandardizedPublishPackage.TargetRef target = new StandardizedPublishPackage.TargetRef();
            target.setDeviceId(targetRef.getDeviceId());
            target.setIp(targetRef.getIp());
            target.setPort(targetRef.getPort());
            target.setVendorHint(targetRef.getVendorHint());
            pkg.setTarget(target);
        }
        return pkg;
    }

    private StandardizedControlPackage extractControl(SecureGatewayEnvelope envelope) throws IOException {
        StandardizedControlPackage pkg = new StandardizedControlPackage();

        pkg.setTaskId(firstNonBlank(envelope.getCommandTaskId(), envelope.getRequestId()));

        // source
        SecureGatewayEnvelope.SourceRef sourceRef = envelope.getSource();
        if (sourceRef != null) {
            StandardizedControlPackage.SourceInfo source = new StandardizedControlPackage.SourceInfo();
            source.setClientId(sourceRef.getClientId());
            source.setClientIp(sourceRef.getClientIp());
            source.setHostName(sourceRef.getHostName());
            source.setOperatorId(sourceRef.getOperatorId());
            pkg.setSource(source);
        }

        // target
        SecureGatewayEnvelope.TargetRef targetRef = envelope.getTarget();
        if (targetRef != null) {
            StandardizedControlPackage.TargetInfo target = new StandardizedControlPackage.TargetInfo();
            target.setDeviceId(targetRef.getDeviceId());
            target.setIp(targetRef.getIp());
            target.setPort(targetRef.getPort());
            target.setVendorHint(targetRef.getVendorHint());
            pkg.setTarget(target);
        }

        // control payload
        SecureGatewayEnvelope.ControlPayload control = envelope.getControl();
        if (control != null) {
            pkg.setAction(control.getAction());
            pkg.setCommand(control.getCommand());
            pkg.setParams(control.getParams());
        }
        return pkg;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 解析结果包装：携带业务 DTO 与信封（可能为 null）。
     */
    public static final class ParseOutcome<T> {
        private final T value;
        private final SecureGatewayEnvelope envelope;

        private ParseOutcome(T value, SecureGatewayEnvelope envelope) {
            this.value = value;
            this.envelope = envelope;
        }

        static <T> ParseOutcome<T> legacy(T value) {
            return new ParseOutcome<>(value, null);
        }

        static <T> ParseOutcome<T> envelope(T value, SecureGatewayEnvelope envelope) {
            return new ParseOutcome<>(value, envelope);
        }

        public T value() {
            return value;
        }

        public SecureGatewayEnvelope envelope() {
            return envelope;
        }

        public boolean isEnvelope() {
            return envelope != null;
        }
    }
}
