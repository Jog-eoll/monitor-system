package com.gateway.device.protocol.common.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.common.JsonCustomMapper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class Thumbs {
    public static final BufferedImage DEFAULT_IMAGE = createImage();
    public static final byte[] DEFAULT_IMAGE_BYTES = encodeImage(DEFAULT_IMAGE);
    public static final String DEFAULT_IMAGE_JSON = buildImageJson(DEFAULT_IMAGE);
    public static final String DEFAULT_THUMBNAIL_NAME = "default_thumb.png";

    private Thumbs() {
    }

    private static BufferedImage createImage() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // 白底
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 64, 64);
        // 黑字
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        String text = "default";
        int x = (64 - fm.stringWidth(text)) / 2;
        int y = (64 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return img;
    }

    private static byte[] encodeImage(BufferedImage image) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bs);
        } catch (IOException e) {
            throw new RuntimeException("编码默认缩略图失败", e);
        }
        return bs.toByteArray();
    }

    /**
     * 将图片编码为带 data URI 前缀的 Base64 JSON 格式
     *
     * @param image 要编码的图片
     * @return JSON 字符串，格式：{"base64":"data:image/png;base64,xxx"}
     */
    public static String buildImageJson(BufferedImage image) {
        byte[] pngBytes = encodeImage(image);
        String base64 = Base64.getEncoder().encodeToString(pngBytes);
        try {
            ObjectNode jsonNode = JsonCustomMapper.get().createObjectNode();
            jsonNode.put("base64", "data:image/png;base64," + base64);
            return JsonCustomMapper.get().writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("构建缩略图 JSON 失败", e);
        }
    }
}
