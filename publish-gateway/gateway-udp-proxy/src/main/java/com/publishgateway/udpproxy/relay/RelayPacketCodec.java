package com.publishgateway.udpproxy.relay;

import lombok.Data;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Decodes Windows transparent relay packets.
 */
public class RelayPacketCodec {

    public static final int MAGIC = 0x49504352; // IPCR
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 104;
    public static final int SIGNATURE_SIZE = 32;
    private static final int PROCESS_NAME_SIZE = 64;
    private static final int FLAG_SIGNED = 0x01;
    private static final int FLAG_CONTENT_TOKEN = 0x02;
    private static final int MAX_EXTENSION_SIZE = 4096;

    public RelayPacket decode(byte[] bytes, boolean verifySignature, String secret) {
        if (bytes == null || bytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("relay packet too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("invalid relay magic");
        }
        int version = buffer.getInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported relay version: " + version);
        }

        RelayPacket packet = new RelayPacket();
        packet.setPid(buffer.getInt() & 0xFFFFFFFFL);
        byte[] processNameBytes = new byte[PROCESS_NAME_SIZE];
        buffer.get(processNameBytes);
        packet.setProcessName(readFixedString(processNameBytes));
        packet.setOriginalSrcIp(readIpv4(buffer.getInt()));
        packet.setOriginalSrcPort(buffer.getShort() & 0xFFFF);
        packet.setOriginalDstIp(readIpv4(buffer.getInt()));
        packet.setOriginalDstPort(buffer.getShort() & 0xFFFF);
        packet.setTimestamp(buffer.getLong());
        int payloadLength = buffer.getInt();
        int flags = buffer.getShort() & 0xFFFF;
        buffer.getShort(); // reserved

        boolean signed = (flags & FLAG_SIGNED) != 0;
        boolean hasContentToken = (flags & FLAG_CONTENT_TOKEN) != 0;
        int cursor = HEADER_SIZE + (signed ? SIGNATURE_SIZE : 0);
        RelayExtension extension = RelayExtension.empty();
        byte[] extensionSection = new byte[0];
        if (hasContentToken) {
            if (bytes.length < cursor + 4) {
                throw new IllegalArgumentException("invalid relay extension length");
            }
            int extensionLength = ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (extensionLength < 0 || extensionLength > MAX_EXTENSION_SIZE || bytes.length < cursor + 4 + extensionLength) {
                throw new IllegalArgumentException("invalid relay extension size");
            }
            extensionSection = Arrays.copyOfRange(bytes, cursor, cursor + 4 + extensionLength);
            extension = parseExtension(bytes, cursor + 4, extensionLength);
            cursor += 4 + extensionLength;
        }
        int payloadOffset = cursor;
        if (payloadLength < 0 || bytes.length < payloadOffset + payloadLength) {
            throw new IllegalArgumentException("invalid relay payload length");
        }
        if (verifySignature) {
            if (!signed) {
                throw new IllegalArgumentException("relay packet signature missing");
            }
            verify(bytes, extensionSection, payloadOffset, payloadLength, secret);
        }
        packet.setSigned(signed);
        packet.setContentTokenId(extension.contentTokenId);
        packet.setContentFileId(extension.contentFileId);
        packet.setPayload(Arrays.copyOfRange(bytes, payloadOffset, payloadOffset + payloadLength));
        return packet;
    }

    public byte[] encode(RelayPacket packet, boolean signPacket, String secret) {
        if (packet == null || packet.getPayload() == null) {
            throw new IllegalArgumentException("relay packet and payload must not be null");
        }
        byte[] payload = packet.getPayload();
        byte[] extension = buildExtension(packet);
        byte[] extensionSection = extension.length == 0 ? new byte[0] : withLengthPrefix(extension);
        int signatureSize = signPacket ? SIGNATURE_SIZE : 0;
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
        int flags = (signPacket ? FLAG_SIGNED : 0) | (extensionSection.length > 0 ? FLAG_CONTENT_TOKEN : 0);
        buffer.putShort((short) flags);
        buffer.putShort((short) 0);
        if (signPacket) {
            byte[] header = Arrays.copyOf(buffer.array(), HEADER_SIZE);
            buffer.put(sign(header, extensionSection, payload, secret));
        }
        buffer.put(extensionSection);
        buffer.put(payload);
        return buffer.array();
    }

    private void verify(byte[] bytes, byte[] extensionSection, int payloadOffset, int payloadLength, String secret) {
        try {
            if (secret == null || secret.trim().isEmpty()) {
                throw new IllegalArgumentException("client-relay.signature-secret is required");
            }
            byte[] actual = Arrays.copyOfRange(bytes, HEADER_SIZE, HEADER_SIZE + SIGNATURE_SIZE);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(bytes, 0, HEADER_SIZE);
            mac.update(extensionSection);
            mac.update(bytes, payloadOffset, payloadLength);
            byte[] expected = mac.doFinal();
            if (!MessageDigestUtil.constantTimeEquals(expected, actual)) {
                throw new IllegalArgumentException("relay signature mismatch");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("relay signature verification failed", e);
        }
    }

    private byte[] sign(byte[] header, byte[] extensionSection, byte[] payload, String secret) {
        try {
            if (secret == null || secret.trim().isEmpty()) {
                throw new IllegalArgumentException("client-relay.signature-secret is required");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(header);
            mac.update(extensionSection);
            mac.update(payload);
            return mac.doFinal();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("relay signature creation failed", e);
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

    private void putFixedString(ByteBuffer buffer, String value, int length) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        int copyLen = Math.min(bytes.length, length);
        buffer.put(bytes, 0, copyLen);
        for (int i = copyLen; i < length; i++) {
            buffer.put((byte) 0);
        }
    }

    private int ipToInt(String ip) {
        try {
            byte[] bytes = java.net.InetAddress.getByName(ip).getAddress();
            if (bytes.length != 4) {
                throw new IllegalArgumentException("only IPv4 relay header is supported");
            }
            return ((bytes[0] & 0xFF) << 24)
                    | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8)
                    | (bytes[3] & 0xFF);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid IPv4 address: " + ip, e);
        }
    }

    private String readFixedString(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    private String readIpv4(int value) {
        return ((value >>> 24) & 0xFF) + "."
                + ((value >>> 16) & 0xFF) + "."
                + ((value >>> 8) & 0xFF) + "."
                + (value & 0xFF);
    }

    private static class MessageDigestUtil {
        private static boolean constantTimeEquals(byte[] a, byte[] b) {
            if (a == null || b == null || a.length != b.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < a.length; i++) {
                result |= a[i] ^ b[i];
            }
            return result == 0;
        }
    }

    @Data
    public static class RelayPacket {
        private long pid;
        private String processName;
        private String originalSrcIp;
        private int originalSrcPort;
        private String originalDstIp;
        private int originalDstPort;
        private long timestamp;
        private boolean signed;
        private String contentTokenId;
        private String contentFileId;
        private byte[] payload;
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
