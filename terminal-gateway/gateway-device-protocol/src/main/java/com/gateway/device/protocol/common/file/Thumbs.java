package com.gateway.device.protocol.common.file;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Thumbs {
    public static final BufferedImage DEFAULT_IMAGE = createImage();
    public static final byte[] DEFAULT_IMAGE_BYTES = encodePng(DEFAULT_IMAGE);
    public static final String DEFAULT_THUMBNAIL_NAME = "default_thumb.png";

    private Thumbs() {
    }

    private static BufferedImage createImage() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        String text = "default";
        int x = (64 - fm.stringWidth(text)) / 2;
        int y = (64 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return img;
    }

    private static byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
        } catch (IOException e) {
            throw new RuntimeException("编码默认缩略图失败", e);
        }
        return baos.toByteArray();
    }
}
