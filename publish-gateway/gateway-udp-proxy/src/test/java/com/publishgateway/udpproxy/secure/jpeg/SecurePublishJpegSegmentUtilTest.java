package com.publishgateway.udpproxy.secure.jpeg;

import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSegmentUtil.SecurePublishJpegBlockInfo;
import org.junit.Assert;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class SecurePublishJpegSegmentUtilTest {

    @Test
    public void shouldWriteAndReadApp11Block() throws Exception {
        byte[] jpeg = createJpeg();
        byte[] signed = SecurePublishJpegSegmentUtil.writeSecurePublishBlock(
                jpeg, SecurePublishJpegSegmentUtil.SEGMENT_APP11, "sample.jpg");

        SecurePublishJpegBlockInfo info = SecurePublishJpegSegmentUtil.inspect(signed);

        Assert.assertTrue(info.getPresent());
        Assert.assertEquals(SecurePublishJpegSegmentUtil.SEGMENT_APP11, info.getSegmentType());
        Assert.assertEquals(SecurePublishJpegSegmentUtil.sha256Hex(jpeg), info.getPayloadSha256());
        Assert.assertEquals(info.getPayloadSha256(), info.getStrippedPayloadSha256());
        Assert.assertTrue(info.getPayloadHashMatched());
        Assert.assertArrayEquals(jpeg, SecurePublishJpegSegmentUtil.stripSecurePublishBlocks(signed));
        Assert.assertFalse(SecurePublishJpegSegmentUtil.sha256Hex(jpeg)
                .equals(SecurePublishJpegSegmentUtil.sha256Hex(signed)));
    }

    @Test
    public void shouldWriteAndReadComBlock() throws Exception {
        byte[] jpeg = createJpeg();
        byte[] signed = SecurePublishJpegSegmentUtil.writeSecurePublishBlock(
                jpeg, SecurePublishJpegSegmentUtil.SEGMENT_COM, "sample.jpg");

        SecurePublishJpegBlockInfo info = SecurePublishJpegSegmentUtil.inspect(signed);

        Assert.assertTrue(info.getPresent());
        Assert.assertEquals(SecurePublishJpegSegmentUtil.SEGMENT_COM, info.getSegmentType());
        Assert.assertEquals(SecurePublishJpegSegmentUtil.sha256Hex(jpeg), info.getPayloadSha256());
        Assert.assertTrue(info.getPayloadHashMatched());
    }

    private byte[] createJpeg() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 16, 16);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(4, 4, 8, 8);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Assert.assertTrue(ImageIO.write(image, "jpg", output));
        return output.toByteArray();
    }
}
