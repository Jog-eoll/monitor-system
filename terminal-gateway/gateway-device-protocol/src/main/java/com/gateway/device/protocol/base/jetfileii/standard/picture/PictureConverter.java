package com.gateway.device.protocol.base.jetfileii.standard.picture;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileMagic;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 普通图片 → ArrayPictureFile 格式转换器 DEMO。
 * <p>
 * 支持将 PNG/JPEG/BMP 等常见图片转为情报板可识别的 ArrayPictureFile 点阵格式。
 * <p>
 * 转换流程:
 * 1. 读取源图片，缩放到目标宽高
 * 2. 遍历每个像素，按目标 PictureType 映射为 LED 色深
 * 3. Z 字排列生成点阵数据
 * 4. 组装 PictureFileHeader + FrameHead + PixelData → ArrayPictureFile
 */
public class PictureConverter {

    private PictureConverter() {
    }

    /**
     * 从图片文件转换为 ArrayPictureFile（单帧）。
     */
    public static ArrayPictureFile fromImage(File imageFile, int targetW, int targetH,
                                             PictureType pictureType) throws IOException {
        BufferedImage src = ImageIO.read(imageFile);
        if (src == null) throw new IOException("无法读取图片: " + imageFile);
        BufferedImage scaled = resize(src, targetW, targetH);
        return fromBufferedImage(scaled, pictureType);
    }

    /**
     * 从 BufferedImage 转换（已缩放至目标尺寸）。
     */
    public static ArrayPictureFile fromBufferedImage(BufferedImage image, PictureType pictureType) {
        int w = image.getWidth();
        int h = image.getHeight();

        byte[] pixelData = convertPixels(image, pictureType, w, h);
        int frameDataSize = pictureType.calcFrameDataSize(w, h);

        ArrayPictureFileHeader header = ArrayPictureFileHeader.builder()
                .head(FileMagic.ARRAY_PIC_HEAD.clone())
                .type((byte) pictureType.getTypeCode())
                .flag((byte) 0)
                .width((short) w)
                .height((short) h)
                .bitPerPoint((short) pictureType.getBitsPerPoint())
                .totalFrame((short) 1)
                .dataSize(frameDataSize + calcFrameHeadSize(h))
                .frameDataSize(frameDataSize)
                .lastDataWidth((short) 0)
                .reserved((short) 0)
                .build();

        FrameHead frameHead = buildDefaultFrameHead(h);

        return ArrayPictureFile.builder()
                .header(header)
                .frameHeads(new FrameHead[]{frameHead})
                .framePixelData(new byte[][]{pixelData})
                .extFrameStructs(null)
                .build();
    }

    /**
     * 将 ArrayPictureFile 序列化为字节数组，可直接作为第二类通信 Data 字段发送。
     * PictureFileHeader(31B) | FrameHead[N] | PixelData[N] | ExtFrameStruct[N]
     */
    public static byte[] toBytes(ArrayPictureFile apf) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(apf.getHeader().getHead());
            bos.write(apf.getHeader().getType());
            bos.write(apf.getHeader().getFlag());
            bos.write(LittleEndianByteBufUtils.shortToLE(apf.getHeader().getWidth()));
            bos.write(LittleEndianByteBufUtils.shortToLE(apf.getHeader().getHeight()));
            bos.write(LittleEndianByteBufUtils.shortToLE(apf.getHeader().getBitPerPoint()));
            bos.write(LittleEndianByteBufUtils.shortToLE(apf.getHeader().getTotalFrame()));
            bos.write(LittleEndianByteBufUtils.intToLE(apf.getHeader().getDataSize()));
            bos.write(LittleEndianByteBufUtils.intToLE(apf.getHeader().getFrameDataSize()));
            bos.write(LittleEndianByteBufUtils.shortToLE(apf.getHeader().getLastDataWidth()));
            bos.write(LittleEndianByteBufUtils.shortToLE(apf.getHeader().getReserved()));

            FrameHead[] frames = apf.getFrameHeads();
            byte[][] pixelData = apf.getFramePixelData();
            for (int i = 0; i < frames.length; i++) {
                bos.write(frameHeadToBytes(frames[i]));
                bos.write(pixelData[i]);
            }
            if (apf.getExtFrameStructs() != null) {
                for (byte[] ext : apf.getExtFrameStructs()) bos.write(ext);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }

    // ── 像素转换核心 ──────────────────────────────

    private static byte[] convertPixels(BufferedImage img, PictureType type, int w, int h) {
        int totalBytes = type.calcFrameDataSize(w, h);
        byte[] buf = new byte[totalBytes];
        int byteIdx = 0;
        int bitPos = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int encoded = encodePixel(r, g, b, type);
                writeBits(buf, byteIdx, bitPos, encoded, type.getBitsPerPoint());
                bitPos += type.getBitsPerPoint();
                while (bitPos >= 8) {
                    bitPos -= 8;
                    byteIdx++;
                }
            }
        }
        return buf;
    }

    private static int encodePixel(int r, int g, int b, PictureType type) {
        switch (type) {
            case RGRGRGRG: {
                int rBit = (r > 127) ? 1 : 0;
                int gBit = (g > 127) ? 1 : 0;
                return (gBit << 1) | rBit;
            }
            case RG:
                return ((r & 0xFF) << 8) | (g & 0xFF);
            case BRG: {
                int b5 = (b >> 3) & 0x1F;
                int r6 = (r >> 2) & 0x3F;
                int g5 = (g >> 3) & 0x1F;
                return (b5 << 11) | (r6 << 5) | g5;
            }
            case BGRN:
                return ((b & 0xFF) << 24) | ((g & 0xFF) << 16) | ((r & 0xFF) << 8);
            case RGYW:
                if (r > 200 && g > 200 && b > 200) return 3;
                if (r > 200 && g > 200) return 2;
                if (g > 127) return 1;
                return 0;
            default:
                return 0;
        }
    }

    private static void writeBits(byte[] buf, int byteIdx, int bitPos, int value, int nBits) {
        int mask = (1 << nBits) - 1;
        value &= mask;
        buf[byteIdx] |= (byte) (value << bitPos);
        if (bitPos + nBits > 8 && byteIdx + 1 < buf.length) {
            int overflow = (bitPos + nBits) - 8;
            buf[byteIdx + 1] |= (byte) (value >> (nBits - overflow));
            buf[byteIdx] &= (byte) ~((mask >> overflow) << bitPos);
            buf[byteIdx] |= (byte) (value << bitPos);
        }
    }

    // ── 辅助 ──────────────────────────────────────

    private static FrameHead buildDefaultFrameHead(int height) {
        int rows = Math.min(height, 16);
        RowState[] states = new RowState[rows];
        for (int i = 0; i < rows; i++) {
            states[i] = RowState.builder()
                    .rowHeight((short) 1)
                    .inEffect((byte) 'd')
                    .outEffect((byte) 'd')
                    .build();
        }
        return FrameHead.builder()
                .headFlag((short) 0x4846)
                .rows((byte) rows)
                .extRows((byte) Math.max(0, height - 16))
                .rowStates(states)
                .timeType((byte) 1)
                .speed((byte) '4')
                .direction((short) 0)
                .stayTime(3)
                .reserved(0)
                .build();
    }

    private static int calcFrameHeadSize(int height) {
        return 4 + Math.min(height, 16) * 4 + 12;
    }

    private static byte[] frameHeadToBytes(FrameHead fh) {
        int rows = fh.getRows() & 0xFF;
        int size = 4 + rows * 4 + 12;
        ByteBuffer bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(fh.getHeadFlag());
        bb.put(fh.getRows());
        bb.put(fh.getExtRows());
        RowState[] states = fh.getRowStates();
        for (int i = 0; i < rows; i++) {
            RowState rs = (i < states.length) ? states[i]
                    : RowState.builder().rowHeight((short) 1).inEffect((byte) 0).outEffect((byte) 0).build();
            bb.putShort(rs.getRowHeight());
            bb.put(rs.getInEffect());
            bb.put(rs.getOutEffect());
        }
        bb.put(fh.getTimeType());
        bb.put(fh.getSpeed());
        bb.putShort(fh.getDirection());
        bb.putInt(fh.getStayTime());
        bb.putInt(fh.getReserved());
        return bb.array();
    }


    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }
}
