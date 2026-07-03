package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.TransparentProxyProperties;
import com.infopublish.client.service.RelayPacketCodec;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Binary relay packet codec.
 */
@Service
public class RelayPacketCodecImpl implements RelayPacketCodec {

    public static final int MAGIC = 0x49504352; // IPCR
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 104;
    public static final int SIGNATURE_SIZE = 32;
    private static final int PROCESS_NAME_SIZE = 64;
    private static final int FLAG_SIGNED = 0x01;
    private static final int FLAG_CONTENT_TOKEN = 0x02;
    private static final int MAX_EXTENSION_SIZE = 4096;

    @Resource
    private TransparentProxyProperties properties;

    @Override
    public byte[] encode(RelayPacket packet) {
        if (packet == null || packet.getPayload() == null) {
            throw new IllegalArgumentException("relay packet and payload must not be null");
        }
        byte[] payload = packet.getPayload();
        boolean signed = properties.isSignPacket();
        byte[] extension = buildExtension(packet);
        byte[] extensionSection = extension.length == 0 ? new byte[0] : withLengthPrefix(extension);
        int signatureSize = signed ? SIGNATURE_SIZE : 0;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + signatureSize + extensionSection.length + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt((int) Math.max(0L, Math.min(packet.getPid(), 0xFFFFFFFFL)));
        putFixedString(buffer, packet.getProcessName(), PROCESS_NAME_SIZE);
        buffer.putInt(ipToInt(packet.getOriginalSrcIp()));
        buffer.putShort((short) packet.getOriginalSrcPort());
        buffer.putInt(ipToInt(packet.getOriginalDstIp()));
        buffer.putShort((short) packet.getOriginalDstPort());
        buffer.putLong(packet.getTimestamp());
        buffer.putInt(payload.length);
        int flags = (signed ? FLAG_SIGNED : 0) | (extensionSection.length > 0 ? FLAG_CONTENT_TOKEN : 0);
        buffer.putShort((short) flags);
        buffer.putShort((short) 0);
        if (signed) {
            byte[] header = Arrays.copyOf(buffer.array(), HEADER_SIZE);
            buffer.put(sign(header, extensionSection, payload));
        }
        buffer.put(extensionSection);
        buffer.put(payload);
        return buffer.array();
    }

    @Override
    public RelayPacket decode(byte[] bytes, int length) {
        if (bytes == null || length < HEADER_SIZE || length > bytes.length) {
            throw new IllegalArgumentException("relay packet too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("invalid relay magic");
        }
        int version = buffer.getInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported relay version: " + version);
        }

        long pid = buffer.getInt() & 0xFFFFFFFFL;
        byte[] processNameBytes = new byte[PROCESS_NAME_SIZE];
        buffer.get(processNameBytes);
        String processName = readFixedString(processNameBytes);
        String originalSrcIp = intToIp(buffer.getInt());
        int originalSrcPort = buffer.getShort() & 0xFFFF;
        String originalDstIp = intToIp(buffer.getInt());
        int originalDstPort = buffer.getShort() & 0xFFFF;
        long timestamp = buffer.getLong();
        int payloadLength = buffer.getInt();
        int flags = buffer.getShort() & 0xFFFF;
        buffer.getShort();

        boolean signed = (flags & FLAG_SIGNED) != 0;
        boolean hasContentToken = (flags & FLAG_CONTENT_TOKEN) != 0;
        int cursor = HEADER_SIZE + (signed ? SIGNATURE_SIZE : 0);
        RelayExtension extension = RelayExtension.empty();
        byte[] extensionSection = new byte[0];
        if (hasContentToken) {
            if (length < cursor + 4) {
                throw new IllegalArgumentException("invalid relay extension length");
            }
            int extensionLength = ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (extensionLength < 0 || extensionLength > MAX_EXTENSION_SIZE || length < cursor + 4 + extensionLength) {
                throw new IllegalArgumentException("invalid relay extension size");
            }
            extensionSection = Arrays.copyOfRange(bytes, cursor, cursor + 4 + extensionLength);
            extension = parseExtension(bytes, cursor + 4, extensionLength);
            cursor += 4 + extensionLength;
        }
        int payloadOffset = cursor;
        if (payloadLength < 0 || length < payloadOffset + payloadLength) {
            throw new IllegalArgumentException("invalid relay payload length");
        }
        if (signed && properties.isSignPacket()) {
            verify(bytes, extensionSection, payloadOffset, payloadLength);
        }

        byte[] payload = Arrays.copyOfRange(bytes, payloadOffset, payloadOffset + payloadLength);
        return new RelayPacket(pid, processName, originalSrcIp, originalSrcPort,
                originalDstIp, originalDstPort, timestamp, payload,
                extension.contentTokenId, extension.contentFileId);
    }

    private byte[] sign(byte[] header, byte[] extensionSection, byte[] payload) {
        try {
            String secret = properties.getSignatureSecret();
            if (secret == null || secret.trim().isEmpty()) {
                throw new IllegalStateException("transparent-proxy.signature-secret is required when sign-packet=true");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(header);
            mac.update(extensionSection);
            mac.update(payload);
            return mac.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException("relay packet signature failed", e);
        }
    }

    private void verify(byte[] bytes, byte[] extensionSection, int payloadOffset, int payloadLength) {
        try {
            String secret = properties.getSignatureSecret();
            if (secret == null || secret.trim().isEmpty()) {
                throw new IllegalStateException("transparent-proxy.signature-secret is required when sign-packet=true");
            }
            byte[] actual = Arrays.copyOfRange(bytes, HEADER_SIZE, HEADER_SIZE + SIGNATURE_SIZE);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(bytes, 0, HEADER_SIZE);
            mac.update(extensionSection);
            mac.update(bytes, payloadOffset, payloadLength);
            byte[] expected = mac.doFinal();
            if (!constantTimeEquals(expected, actual)) {
                throw new IllegalArgumentException("relay signature mismatch");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("relay packet signature verification failed", e);
        }
    }

    private byte[] buildExtension(RelayPacket packet) {
        byte[] tokenBytes = fixedBytes(packet.getContentTokenId());
        byte[] fileBytes = fixedBytes(packet.getContentFileId());
        if (tokenBytes.length == 0 && fileBytes.length == 0) {
            return new byte[0];
        }
        if (tokenBytes.length > 1024 || fileBytes.length > 1024) {
            throw new IllegalArgumentException("content token extension is too long");
        }
        ByteBuffer buffer = ByteBuffer.allocate(4 + tokenBytes.length + fileBytes.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) tokenBytes.length);
        buffer.putShort((short) fileBytes.length);
        buffer.put(tokenBytes);
        buffer.put(fileBytes);
        return buffer.array();
    }

    private byte[] withLengthPrefix(byte[] extension) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + extension.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(extension.length);
        buffer.put(extension);
        return buffer.array();
    }

    private RelayExtension parseExtension(byte[] bytes, int offset, int length) {
        if (length < 4) {
            throw new IllegalArgumentException("relay extension too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.BIG_ENDIAN);
        int tokenLength = buffer.getShort() & 0xFFFF;
        int fileLength = buffer.getShort() & 0xFFFF;
        if (tokenLength + fileLength != length - 4) {
            throw new IllegalArgumentException("relay extension field length mismatch");
        }
        byte[] tokenBytes = new byte[tokenLength];
        byte[] fileBytes = new byte[fileLength];
        buffer.get(tokenBytes);
        buffer.get(fileBytes);
        return new RelayExtension(
                new String(tokenBytes, StandardCharsets.UTF_8),
                new String(fileBytes, StandardCharsets.UTF_8));
    }

    private byte[] fixedBytes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new byte[0];
        }
        return value.trim().getBytes(StandardCharsets.UTF_8);
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private void putFixedString(ByteBuffer buffer, String value, int length) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        int copyLen = Math.min(bytes.length, length);
        buffer.put(bytes, 0, copyLen);
        for (int i = copyLen; i < length; i++) {
            buffer.put((byte) 0);
        }
    }

    private String readFixedString(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    private int ipToInt(String ip) {
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != 4) {
                throw new IllegalArgumentException("only IPv4 relay header is supported in phase 3");
            }
            return ((bytes[0] & 0xFF) << 24)
                    | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8)
                    | (bytes[3] & 0xFF);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid IPv4 address: " + ip, e);
        }
    }

    private String intToIp(int value) {
        return ((value >>> 24) & 0xFF) + "."
                + ((value >>> 16) & 0xFF) + "."
                + ((value >>> 8) & 0xFF) + "."
                + (value & 0xFF);
    }

    private static class RelayExtension {
        private final String contentTokenId;
        private final String contentFileId;

        private RelayExtension(String contentTokenId, String contentFileId) {
            this.contentTokenId = contentTokenId;
            this.contentFileId = contentFileId;
        }

        private static RelayExtension empty() {
            return new RelayExtension(null, null);
        }
    }
}
