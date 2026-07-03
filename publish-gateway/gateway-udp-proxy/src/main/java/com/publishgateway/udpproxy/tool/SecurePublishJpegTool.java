package com.publishgateway.udpproxy.tool;

import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSegmentUtil;
import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSegmentUtil.SecurePublishJpegBlockInfo;
import com.publishgateway.udpproxy.service.impl.SimpleCryptoServiceImpl;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal command line tool for writing and inspecting SecurePublish JPEG segments.
 */
public final class SecurePublishJpegTool {

    private SecurePublishJpegTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 2) {
            printUsage();
            return;
        }

        String command = args[0];
        if ("write".equalsIgnoreCase(command)) {
            write(args);
            return;
        }
        if ("sign-mock".equalsIgnoreCase(command)) {
            signMock(args);
            return;
        }
        if ("sample".equalsIgnoreCase(command)) {
            sample(args[1]);
            return;
        }
        if ("inspect".equalsIgnoreCase(command)) {
            inspect(args[1]);
            return;
        }
        printUsage();
    }

    private static void write(String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        Path input = Paths.get(args[1]);
        Path output = Paths.get(args[2]);
        String segment = args.length >= 4 ? args[3] : SecurePublishJpegSegmentUtil.SEGMENT_APP11;

        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("input file does not exist: " + input);
        }
        byte[] inputBytes = Files.readAllBytes(input);
        byte[] signedBytes = SecurePublishJpegSegmentUtil.writeSecurePublishBlock(
                inputBytes, segment, input.getFileName().toString());
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, signedBytes);

        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("output=" + output.toAbsolutePath());
        System.out.println("segment=" + segment.toUpperCase());
        System.out.println("unsignedPayloadSha256="
                + SecurePublishJpegSegmentUtil.sha256Hex(SecurePublishJpegSegmentUtil.stripSecurePublishBlocks(signedBytes)));
        System.out.println("signedJpegSha256=" + SecurePublishJpegSegmentUtil.sha256Hex(signedBytes));
        printBlockInfo(SecurePublishJpegSegmentUtil.inspect(signedBytes));
    }

    private static void signMock(String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        Path input = Paths.get(args[1]);
        Path output = Paths.get(args[2]);
        String segment = args.length >= 4 ? args[3] : SecurePublishJpegSegmentUtil.SEGMENT_APP11;
        long ttlSeconds = args.length >= 5 ? Long.parseLong(args[4]) : 86_400L;

        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("input file does not exist: " + input);
        }
        byte[] inputBytes = Files.readAllBytes(input);
        byte[] unsignedBytes = SecurePublishJpegSegmentUtil.stripSecurePublishBlocks(inputBytes);
        String payloadSha256 = SecurePublishJpegSegmentUtil.sha256Hex(unsignedBytes);
        long now = System.currentTimeMillis();
        String manifestJson = SecurePublishJpegSegmentUtil.buildManifestJson(
                input.getFileName().toString(),
                unsignedBytes.length,
                payloadSha256,
                "mock-signer",
                "mock",
                now,
                now + ttlSeconds * 1000L,
                "MOCK-AES-SIGNED-ENVELOPE");
        byte[] signedEnvelope = new SimpleCryptoServiceImpl().signEnvelope(manifestJson.getBytes(StandardCharsets.UTF_8));
        byte[] signedBytes = SecurePublishJpegSegmentUtil.writeSignedSecurePublishBlock(
                unsignedBytes, segment, manifestJson, signedEnvelope);

        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, signedBytes);

        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("output=" + output.toAbsolutePath());
        System.out.println("segment=" + segment.toUpperCase());
        System.out.println("payloadSha256=" + payloadSha256);
        System.out.println("signedJpegSha256=" + SecurePublishJpegSegmentUtil.sha256Hex(signedBytes));
        System.out.println("manifestJson=" + manifestJson);
        printBlockInfo(SecurePublishJpegSegmentUtil.inspect(signedBytes));
    }

    private static void inspect(String pathText) throws Exception {
        Path path = Paths.get(pathText);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("file does not exist: " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        System.out.println("file=" + path.toAbsolutePath());
        System.out.println("fileSha256=" + SecurePublishJpegSegmentUtil.sha256Hex(bytes));
        printBlockInfo(SecurePublishJpegSegmentUtil.inspect(bytes));
    }

    private static void printBlockInfo(SecurePublishJpegBlockInfo info) {
        if (info == null || !Boolean.TRUE.equals(info.getPresent())) {
            System.out.println("securePublishPresent=false");
            if (info != null && info.getError() != null) {
                System.out.println("securePublishError=" + info.getError());
            }
            return;
        }
        System.out.println("securePublishPresent=true");
        System.out.println("securePublishSegment=" + info.getSegmentType());
        System.out.println("securePublishPayloadSha256=" + info.getPayloadSha256());
        System.out.println("securePublishStrippedPayloadSha256=" + info.getStrippedPayloadSha256());
        System.out.println("securePublishPayloadHashMatched=" + info.getPayloadHashMatched());
        System.out.println("securePublishSignatureAlgorithm=" + info.getSignatureAlgorithm());
        System.out.println("securePublishSignature=" + info.getSignature());
        System.out.println("securePublishKeyId=" + info.getKeyId());
        System.out.println("securePublishManifestJsonPresent=" + (info.getManifestJson() != null && !info.getManifestJson().isEmpty()));
        System.out.println("securePublishSignedEnvelopeBase64Length="
                + (info.getSignedEnvelopeBase64() == null ? 0 : info.getSignedEnvelopeBase64().length()));
    }

    private static void sample(String outputPath) throws Exception {
        Path output = Paths.get(outputPath);
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(16, 64, 96));
            graphics.fillRect(0, 0, image.getWidth(), 90);
            graphics.setColor(new Color(255, 255, 255));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString("SecurePublish JPEG Test", 36, 58);
            graphics.setColor(new Color(45, 122, 174));
            graphics.fillRect(36, 132, 568, 140);
            graphics.setColor(new Color(255, 255, 255));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
            graphics.drawString("APP11 / COM transport validation", 68, 212);
        } finally {
            graphics.dispose();
        }

        if (!ImageIO.write(image, "jpg", output.toFile())) {
            throw new IllegalStateException("No JPEG ImageIO writer found");
        }
        byte[] bytes = Files.readAllBytes(output);
        System.out.println("sample=" + output.toAbsolutePath());
        System.out.println("sampleSha256=" + SecurePublishJpegSegmentUtil.sha256Hex(bytes));
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java ... SecurePublishJpegTool sample <output.jpg>");
        System.out.println("  java ... SecurePublishJpegTool write <input.jpg> <output.jpg> [APP11|COM]");
        System.out.println("  java ... SecurePublishJpegTool sign-mock <input.jpg> <output.jpg> [APP11|COM] [ttlSeconds]");
        System.out.println("  java ... SecurePublishJpegTool inspect <file.jpg>");
    }
}
