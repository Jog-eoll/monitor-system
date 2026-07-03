package com.publishgateway.udpproxy.secure.jpeg;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes the minimal SecurePublish metadata block in JPEG APP11/COM segments.
 */
public final class SecurePublishJpegSegmentUtil {

    public static final String BLOCK_MARKER = "SecurePublish";
    public static final String SEGMENT_APP11 = "APP11";
    public static final String SEGMENT_COM = "COM";

    private static final int MARKER_PREFIX = 0xFF;
    private static final int MARKER_SOI = 0xD8;
    private static final int MARKER_EOI = 0xD9;
    private static final int MARKER_SOS = 0xDA;
    private static final int MARKER_COM = 0xFE;
    private static final int MARKER_APP0 = 0xE0;
    private static final int MARKER_APP15 = 0xEF;
    private static final int MARKER_APP11 = 0xEB;
    private static final int MAX_SEGMENT_LENGTH = 0xFFFF;
    private static final byte[] BLOCK_PREFIX = (BLOCK_MARKER + "\n").getBytes(StandardCharsets.US_ASCII);

    private SecurePublishJpegSegmentUtil() {
    }

    public static byte[] writeSecurePublishBlock(byte[] jpegBytes, String segmentType, String fileName) {
        validateJpeg(jpegBytes);
        String normalizedSegment = normalizeSegmentType(segmentType);
        byte[] unsignedJpeg = stripSecurePublishBlocks(jpegBytes);
        String payloadSha256 = sha256Hex(unsignedJpeg);
        byte[] blockPayload = buildBlockPayload(normalizedSegment, fileName, payloadSha256, System.currentTimeMillis());
        int segmentLength = blockPayload.length + 2;
        if (segmentLength > MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("SecurePublish JPEG segment is too large: " + blockPayload.length);
        }

        int insertOffset = findInsertOffset(unsignedJpeg);
        ByteArrayOutputStream out = new ByteArrayOutputStream(unsignedJpeg.length + blockPayload.length + 4);
        out.write(unsignedJpeg, 0, insertOffset);
        out.write(MARKER_PREFIX);
        out.write(SEGMENT_APP11.equals(normalizedSegment) ? MARKER_APP11 : MARKER_COM);
        writeUnsignedShort(out, segmentLength);
        out.write(blockPayload, 0, blockPayload.length);
        out.write(unsignedJpeg, insertOffset, unsignedJpeg.length - insertOffset);
        return out.toByteArray();
    }

    public static byte[] writeSignedSecurePublishBlock(byte[] jpegBytes,
                                                       String segmentType,
                                                       String manifestJson,
                                                       byte[] signedEnvelope) {
        validateJpeg(jpegBytes);
        if (manifestJson == null || manifestJson.trim().isEmpty()) {
            throw new IllegalArgumentException("manifestJson must not be empty");
        }
        if (signedEnvelope == null || signedEnvelope.length == 0) {
            throw new IllegalArgumentException("signedEnvelope must not be empty");
        }
        String normalizedSegment = normalizeSegmentType(segmentType);
        byte[] unsignedJpeg = stripSecurePublishBlocks(jpegBytes);
        String payloadSha256 = sha256Hex(unsignedJpeg);
        byte[] blockPayload = buildSignedBlockPayload(normalizedSegment, manifestJson, signedEnvelope, payloadSha256);
        int segmentLength = blockPayload.length + 2;
        if (segmentLength > MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("SecurePublish JPEG segment is too large: " + blockPayload.length);
        }

        int insertOffset = findInsertOffset(unsignedJpeg);
        ByteArrayOutputStream out = new ByteArrayOutputStream(unsignedJpeg.length + blockPayload.length + 4);
        out.write(unsignedJpeg, 0, insertOffset);
        out.write(MARKER_PREFIX);
        out.write(SEGMENT_APP11.equals(normalizedSegment) ? MARKER_APP11 : MARKER_COM);
        writeUnsignedShort(out, segmentLength);
        out.write(blockPayload, 0, blockPayload.length);
        out.write(unsignedJpeg, insertOffset, unsignedJpeg.length - insertOffset);
        return out.toByteArray();
    }

    public static byte[] stripSecurePublishBlocks(byte[] jpegBytes) {
        validateJpeg(jpegBytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpegBytes.length);
        out.write(jpegBytes, 0, 2);

        int offset = 2;
        while (offset < jpegBytes.length) {
            Marker marker = nextMarker(jpegBytes, offset);
            if (marker == null) {
                out.write(jpegBytes, offset, jpegBytes.length - offset);
                break;
            }
            if (!hasLength(marker.value)) {
                int markerEnd = marker.markerOffset + 1;
                out.write(jpegBytes, marker.segmentStart, markerEnd - marker.segmentStart);
                offset = markerEnd;
                if (marker.value == MARKER_EOI) {
                    if (offset < jpegBytes.length) {
                        out.write(jpegBytes, offset, jpegBytes.length - offset);
                    }
                    break;
                }
                continue;
            }

            int lengthOffset = marker.markerOffset + 1;
            int segmentLength = readUnsignedShort(jpegBytes, lengthOffset);
            if (segmentLength < 2) {
                throw new IllegalArgumentException("Invalid JPEG segment length at offset " + marker.segmentStart);
            }
            int segmentEnd = lengthOffset + segmentLength;
            if (segmentEnd > jpegBytes.length) {
                throw new IllegalArgumentException("JPEG segment exceeds file length at offset " + marker.segmentStart);
            }

            int payloadOffset = lengthOffset + 2;
            int payloadLength = segmentLength - 2;
            boolean secureBlock = isSecurePublishSegment(marker.value)
                    && startsWithSecurePublishPrefix(jpegBytes, payloadOffset, payloadLength);
            if (!secureBlock) {
                out.write(jpegBytes, marker.segmentStart, segmentEnd - marker.segmentStart);
            }
            offset = segmentEnd;
            if (marker.value == MARKER_SOS) {
                out.write(jpegBytes, offset, jpegBytes.length - offset);
                break;
            }
        }
        return out.toByteArray();
    }

    public static SecurePublishJpegBlockInfo inspect(byte[] jpegBytes) {
        SecurePublishJpegBlockInfo info = new SecurePublishJpegBlockInfo();
        if (jpegBytes == null || jpegBytes.length == 0) {
            info.setPresent(false);
            info.setError("EMPTY_JPEG");
            return info;
        }
        try {
            validateJpeg(jpegBytes);
            SecurePublishJpegBlockInfo found = findFirstBlock(jpegBytes);
            if (!Boolean.TRUE.equals(found.getPresent())) {
                return found;
            }
            byte[] stripped = stripSecurePublishBlocks(jpegBytes);
            String strippedHash = sha256Hex(stripped);
            found.setStrippedPayloadSha256(strippedHash);
            found.setPayloadHashMatched(strippedHash.equalsIgnoreCase(safe(found.getPayloadSha256())));
            return found;
        } catch (Exception e) {
            info.setPresent(false);
            info.setError(e.getMessage());
            return info;
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data == null ? new byte[0] : data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String buildManifestJson(String fileName,
                                           long fileSize,
                                           String payloadSha256,
                                           String publisher,
                                           String keyId,
                                           long signTime,
                                           long expireTime,
                                           String algorithm) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", 1);
        manifest.put("fileName", fileName == null ? "" : fileName);
        manifest.put("fileSize", fileSize);
        manifest.put("mediaType", "image/jpeg");
        manifest.put("hashAlgorithm", "SHA-256");
        manifest.put("payloadSha256", payloadSha256);
        manifest.put("publisher", publisher == null ? "" : publisher);
        manifest.put("contentDecision", "ALLOW");
        manifest.put("keyId", keyId == null ? "" : keyId);
        manifest.put("signTime", signTime);
        manifest.put("expireTime", expireTime);
        manifest.put("algorithm", algorithm == null || algorithm.trim().isEmpty()
                ? "SIGNED_ENVELOPE" : algorithm.trim());
        return JSON.toJSONString(manifest);
    }

    private static SecurePublishJpegBlockInfo findFirstBlock(byte[] jpegBytes) {
        SecurePublishJpegBlockInfo info = new SecurePublishJpegBlockInfo();
        info.setPresent(false);

        int offset = 2;
        while (offset < jpegBytes.length) {
            Marker marker = nextMarker(jpegBytes, offset);
            if (marker == null || !hasLength(marker.value)) {
                return info;
            }

            int lengthOffset = marker.markerOffset + 1;
            int segmentLength = readUnsignedShort(jpegBytes, lengthOffset);
            if (segmentLength < 2) {
                throw new IllegalArgumentException("Invalid JPEG segment length at offset " + marker.segmentStart);
            }
            int segmentEnd = lengthOffset + segmentLength;
            if (segmentEnd > jpegBytes.length) {
                throw new IllegalArgumentException("JPEG segment exceeds file length at offset " + marker.segmentStart);
            }

            int payloadOffset = lengthOffset + 2;
            int payloadLength = segmentLength - 2;
            if (isSecurePublishSegment(marker.value)
                    && startsWithSecurePublishPrefix(jpegBytes, payloadOffset, payloadLength)) {
                String json = readJsonPayload(jpegBytes, payloadOffset, payloadLength);
                JSONObject object = JSON.parseObject(json);
                info.setPresent(true);
                info.setSegmentType(marker.value == MARKER_APP11 ? SEGMENT_APP11 : SEGMENT_COM);
                info.setSegmentOffset(marker.segmentStart);
                info.setSegmentLength(segmentEnd - marker.segmentStart);
                info.setMarker(object.getString("marker"));
                info.setVersion(object.getInteger("version"));
                info.setHashAlgorithm(object.getString("hashAlgorithm"));
                info.setPayloadSha256(object.getString("payloadSha256"));
                info.setSignatureAlgorithm(object.getString("signatureAlgorithm"));
                info.setSignature(object.getString("signature"));
                info.setCreatedAt(object.getLong("createdAt"));
                info.setFileName(object.getString("fileName"));
                info.setManifestJson(object.getString("manifestJson"));
                info.setSignedEnvelopeBase64(object.getString("signedEnvelopeBase64"));
                info.setKeyId(object.getString("keyId"));
                info.setBlockJson(json);
                return info;
            }

            offset = segmentEnd;
            if (marker.value == MARKER_SOS) {
                return info;
            }
        }
        return info;
    }

    private static byte[] buildBlockPayload(String segmentType, String fileName, String payloadSha256, long createdAt) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("marker", BLOCK_MARKER);
        block.put("version", 1);
        block.put("container", "JPEG_SEGMENT");
        block.put("segment", segmentType);
        block.put("fileName", fileName == null ? "" : fileName);
        block.put("hashAlgorithm", "SHA-256");
        block.put("payloadSha256", payloadSha256);
        block.put("createdAt", createdAt);
        block.put("signatureAlgorithm", "NONE_MINIMAL_VALIDATION");
        block.put("signature", "sha256:" + payloadSha256);

        byte[] json = JSON.toJSONString(block).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(BLOCK_PREFIX.length + json.length);
        out.write(BLOCK_PREFIX, 0, BLOCK_PREFIX.length);
        out.write(json, 0, json.length);
        return out.toByteArray();
    }

    private static byte[] buildSignedBlockPayload(String segmentType,
                                                  String manifestJson,
                                                  byte[] signedEnvelope,
                                                  String payloadSha256) {
        JSONObject manifest = JSON.parseObject(manifestJson);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("marker", BLOCK_MARKER);
        block.put("version", 2);
        block.put("container", "JPEG_SEGMENT");
        block.put("segment", segmentType);
        block.put("fileName", manifest.getString("fileName"));
        block.put("hashAlgorithm", "SHA-256");
        block.put("payloadSha256", payloadSha256);
        block.put("createdAt", System.currentTimeMillis());
        block.put("signatureAlgorithm", manifest.getString("algorithm"));
        block.put("keyId", manifest.getString("keyId"));
        block.put("manifestJson", manifestJson);
        block.put("signedEnvelopeBase64", Base64.getEncoder().encodeToString(signedEnvelope));

        byte[] json = JSON.toJSONString(block).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(BLOCK_PREFIX.length + json.length);
        out.write(BLOCK_PREFIX, 0, BLOCK_PREFIX.length);
        out.write(json, 0, json.length);
        return out.toByteArray();
    }

    private static int findInsertOffset(byte[] jpegBytes) {
        int offset = 2;
        while (offset < jpegBytes.length) {
            Marker marker = nextMarker(jpegBytes, offset);
            if (marker == null || !hasLength(marker.value)) {
                return offset;
            }
            int lengthOffset = marker.markerOffset + 1;
            int segmentLength = readUnsignedShort(jpegBytes, lengthOffset);
            if (segmentLength < 2) {
                throw new IllegalArgumentException("Invalid JPEG segment length at offset " + marker.segmentStart);
            }
            int segmentEnd = lengthOffset + segmentLength;
            if (segmentEnd > jpegBytes.length) {
                throw new IllegalArgumentException("JPEG segment exceeds file length at offset " + marker.segmentStart);
            }
            if (!isMetadataSegment(marker.value)) {
                return marker.segmentStart;
            }
            offset = segmentEnd;
        }
        return 2;
    }

    private static Marker nextMarker(byte[] data, int start) {
        int offset = start;
        while (offset < data.length && (data[offset] & 0xFF) != MARKER_PREFIX) {
            offset++;
        }
        if (offset >= data.length - 1) {
            return null;
        }
        int markerOffset = offset + 1;
        while (markerOffset < data.length && (data[markerOffset] & 0xFF) == MARKER_PREFIX) {
            markerOffset++;
        }
        if (markerOffset >= data.length) {
            return null;
        }
        int marker = data[markerOffset] & 0xFF;
        if (marker == 0x00) {
            return null;
        }
        return new Marker(offset, markerOffset, marker);
    }

    private static boolean hasLength(int marker) {
        return marker != MARKER_SOI
                && marker != MARKER_EOI
                && marker != 0x01
                && (marker < 0xD0 || marker > 0xD7);
    }

    private static boolean isMetadataSegment(int marker) {
        return (marker >= MARKER_APP0 && marker <= MARKER_APP15) || marker == MARKER_COM;
    }

    private static boolean isSecurePublishSegment(int marker) {
        return marker == MARKER_APP11 || marker == MARKER_COM;
    }

    private static boolean startsWithSecurePublishPrefix(byte[] data, int offset, int length) {
        if (length < BLOCK_PREFIX.length || offset < 0 || offset + length > data.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(data, offset, offset + BLOCK_PREFIX.length), BLOCK_PREFIX);
    }

    private static String readJsonPayload(byte[] data, int offset, int length) {
        int jsonOffset = offset + BLOCK_PREFIX.length;
        int jsonLength = length - BLOCK_PREFIX.length;
        return new String(data, jsonOffset, jsonLength, StandardCharsets.UTF_8);
    }

    private static String normalizeSegmentType(String segmentType) {
        String value = segmentType == null ? SEGMENT_APP11 : segmentType.trim().toUpperCase(Locale.ROOT);
        if (SEGMENT_APP11.equals(value) || SEGMENT_COM.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("segmentType must be APP11 or COM");
    }

    private static void validateJpeg(byte[] data) {
        if (data == null || data.length < 4
                || (data[0] & 0xFF) != MARKER_PREFIX
                || (data[1] & 0xFF) != MARKER_SOI) {
            throw new IllegalArgumentException("Input is not a JPEG file");
        }
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        if (offset < 0 || offset + 1 >= data.length) {
            throw new IllegalArgumentException("Cannot read JPEG segment length at offset " + offset);
        }
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeUnsignedShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class Marker {
        private final int segmentStart;
        private final int markerOffset;
        private final int value;

        private Marker(int segmentStart, int markerOffset, int value) {
            this.segmentStart = segmentStart;
            this.markerOffset = markerOffset;
            this.value = value;
        }
    }

    @Data
    public static class SecurePublishJpegBlockInfo {
        private Boolean present;
        private String segmentType;
        private Integer segmentOffset;
        private Integer segmentLength;
        private String marker;
        private Integer version;
        private String hashAlgorithm;
        private String payloadSha256;
        private String strippedPayloadSha256;
        private Boolean payloadHashMatched;
        private String signatureAlgorithm;
        private String signature;
        private Long createdAt;
        private String fileName;
        private String manifestJson;
        private String signedEnvelopeBase64;
        private String keyId;
        private String blockJson;
        private String error;
    }
}
