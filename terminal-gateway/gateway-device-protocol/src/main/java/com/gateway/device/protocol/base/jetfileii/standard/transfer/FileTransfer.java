package com.gateway.device.protocol.base.jetfileii.standard.transfer;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.command.StatusCode;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SequentSysHelper;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SysFileName;
import com.gateway.device.protocol.base.jetfileii.standard.text.FontListFile;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * JetFileII 文件传输工具 — 纯传输层，不包含数据格式解析。
 *
 * <p>路径寻址：所有读写统一使用设备文件系统全路径（0x0108 / 0x0208），
 * 由调用方通过 {@link com.gateway.device.protocol.base.jetfileii.standard.command.FileType#resolvePath} 构建路径。</p>
 *
 * <p>数据解析独立为 {@link FileDataParser}。</p>
 */
@Slf4j
public class FileTransfer {

    private final TransportAdapter transport;
    private int gg = 1;
    private int uu = 1;
    private int packSize = ProtocolConst.DEFAULT_CHUNK_SIZE;
    private boolean verbose = true;

    public FileTransfer(DeviceTransport transport, DeviceContext device) {
        this.transport = new DeviceTransportAdapter(transport, device);
    }

    private static void updatePackIndex(byte[] arg, byte subCmd, int idx) {
        switch (subCmd) {
            case SubCmd.READ_SYSFILE:
            case SubCmd.READ_FONTFILE:
                LittleEndianByteBufUtils.writeUShortLE(arg, 14, idx);
                break;
            case SubCmd.READ_PATHFILE:
                LittleEndianByteBufUtils.writeUShortLE(arg, 2, idx);
                break;
            default:
                LittleEndianByteBufUtils.writeUShortLE(arg, arg.length - 2, idx);
                break;
        }
    }

    /**
     * 从回送 Arg 中提取文件总大小。
     *
     * @param largeFile true=允许32位合并(PICTURE/FONT/SYS/PATH); false=仅用16位(TEXT/STRING)
     */
    static long extractTotalSize(byte[] replyArg, boolean largeFile) {
        if (replyArg == null || replyArg.length < 2) return -1;
        long sizeLow = LittleEndianByteBufUtils.readUShortLE(replyArg, 0);
        long packIdx = replyArg.length >= 4
                ? LittleEndianByteBufUtils.readUShortLE(replyArg, 2) : 0;
        if (!largeFile) {
            log.debug("[FILE] 回送Arg raw={} | sizeLow={} packIdx={}",
                    LittleEndianByteBufUtils.toHex(replyArg), sizeLow, packIdx);
            return sizeLow;
        }
        long sizeHigh = replyArg.length >= 8
                ? LittleEndianByteBufUtils.readUShortLE(replyArg, 4) : 0;
        log.debug("[FILE] 回送Arg raw={} | sizeLow={} packIdx={} sizeHigh={}",
                LittleEndianByteBufUtils.toHex(replyArg), sizeLow, packIdx, sizeHigh);
        if (sizeHigh == sizeLow) return sizeLow;
        return (sizeLow & 0xFFFFL) | ((sizeHigh & 0xFFFFL) << 16);
    }

    private static void checkStatus(PacketMessage reply) throws IOException {
        if (reply.isStatusReply() && !StatusCode.isOk(reply.getStatusCode())) {
            short code = reply.getStatusCode();
            throw new IOException("文件读取失败: 0x" + Integer.toHexString(code & 0xFFFF).toUpperCase());
        }
    }

    // ════════════════════════════════════════════════════
    // 系统文件读取 (0x0102)
    // ════════════════════════════════════════════════════

    private static void checkWriteStatus(PacketMessage reply) throws IOException {
        if (reply.isStatusReply() && !StatusCode.isOk(reply.getStatusCode())) {
            short code = reply.getStatusCode();
            throw new IOException("文件写入失败: 0x" + Integer.toHexString(code & 0xFFFF).toUpperCase());
        }
    }

    // ════════════════════════════════════════════════════
    // 按路径读取 (0x0108)
    // ════════════════════════════════════════════════════

    /**
     * 构建 0x0203 字库写入 Arg (24B, argLen=6)
     */
    private static byte[] buildFontWriteArg(byte[] nameBytes, int fileSize,
                                            int packSize, int totalPacks, int packIndex) {
        byte[] arg = new byte[24];
        System.arraycopy(nameBytes, 0, arg, 0, 12);
        LittleEndianByteBufUtils.writeUIntLE(arg, 12, fileSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 16, packSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 18, totalPacks);
        LittleEndianByteBufUtils.writeUShortLE(arg, 20, packIndex);
        return arg;
    }

    private static byte[] padRight(String s, int len) {
        byte[] out = new byte[len];
        if (s != null) {
            byte[] src = s.getBytes(ProtocolConstant.GB18030);
            System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        }
        return out;
    }

    // ════════════════════════════════════════════════════
    // 内部读取管线
    // ════════════════════════════════════════════════════

    public FileTransfer setAddr(int gg, int uu) {
        this.gg = gg;
        this.uu = uu;
        return this;
    }

    public FileTransfer setPackSize(int ps) {
        this.packSize = ps;
        return this;
    }

    public FileTransfer setVerbose(boolean v) {
        this.verbose = v;
        return this;
    }

    /**
     * 使用 0x0102 按文件名读取系统文件。
     *
     * @param fileName 文件名（最多12字符ASCII，尾部补0x00）
     */
    public byte[] readSysFile(String fileName) throws IOException {
        byte[] nameBytes = padRight(fileName, 12);

        if (verbose) log.debug("[FILE] 读取 {} ...", fileName);

        byte[] arg = new byte[16];
        System.arraycopy(nameBytes, 0, arg, 0, 12);
        LittleEndianByteBufUtils.writeUShortLE(arg, 12, packSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 14, 1);

        byte[] req = PacketBuilder.create(MainCmd.READ, SubCmd.READ_SYSFILE)
                .destAddr(gg, uu).needReply().argWithLen(arg, 4).buildBytes();

        PacketMessage reply = transport.sendAndReceive(req);
        checkStatus(reply);

        long totalSize = extractTotalSize(reply.getArg(), true);
        byte[] chunk = reply.getData();
        if (chunk == null || chunk.length == 0) return new byte[0];

        if (verbose) log.debug("[FILE] 总大小={}B  首包={}B", totalSize, chunk.length);

        if (totalSize <= chunk.length) {
            if (chunk.length > totalSize && totalSize > 0) {
                return Arrays.copyOf(chunk, (int) totalSize);
            }
            return chunk;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(chunk);
        for (int idx = 2; bos.size() < totalSize; idx++) {
            LittleEndianByteBufUtils.writeUShortLE(arg, 14, idx);
            req = PacketBuilder.create(MainCmd.READ, SubCmd.READ_SYSFILE)
                    .destAddr(gg, uu).needReply().argWithLen(arg, 4).buildBytes();
            reply = transport.sendAndReceive(req);
            checkStatus(reply);
            chunk = reply.getData();
            if (chunk == null || chunk.length == 0) break;
            bos.write(chunk);
            if (verbose && idx % 10 == 0) {
                log.debug("[FILE] 续传 #{}  {}/{}B", idx, bos.size(), totalSize);
            }
        }

        byte[] result = bos.toByteArray();
        if (result.length > totalSize && totalSize > 0) {
            result = Arrays.copyOf(result, (int) totalSize);
        }
        if (verbose) log.debug("[FILE] 完成 {}B{}", result.length,
                result.length != totalSize ? " (期望" + totalSize + "B)" : "");
        return result;
    }

    // ════════════════════════════════════════════════════
    // 系统文件写入 (0x0202)
    // ════════════════════════════════════════════════════

    /**
     * 使用 0x0108 按文件系统路径读取（无 CRC）。
     *
     * @param filePath 设备上的文件路径（如 "D:\\P\\bao3.jpg"）
     */
    public byte[] readPathFile(String filePath) throws IOException {
        if (verbose) log.debug("[FILE] 读取路径 '{}' ...", filePath);

        byte[] pathBytes = (filePath + "\0").getBytes(ProtocolConstant.GB18030);
        int argBytes = 4 + pathBytes.length;
        byte[] arg = new byte[argBytes];
        LittleEndianByteBufUtils.writeUShortLE(arg, 0, packSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 2, 1);
        System.arraycopy(pathBytes, 0, arg, 4, pathBytes.length);

        return readChunked(MainCmd.READ, SubCmd.READ_PATHFILE,
                arg, (argBytes + 3) / ProtocolConst.ARG_UNIT_SIZE);
    }

    // ════════════════════════════════════════════════════
    // 按路径写入 (0x0208)
    // ════════════════════════════════════════════════════

    /**
     * 使用 0x0108 + CRC 校验和，按文件系统路径读取。
     *
     * <p>先以 packSize=4 探测文件大小，再以 packSize=DEFAULT_CHUNK_SIZE 分块下载。</p>
     *
     * @param filePath 设备上的文件路径（如 "D:\\F\\HHXXp.mp4"）
     */
    public byte[] readPathFileCrc(String filePath) throws IOException {
        if (verbose) log.debug("[FILE] 读取路径(CRC) '{}' ...", filePath);

        byte[] pathBytes = (filePath + "\0").getBytes(ProtocolConstant.GB18030);
        int argBytes = 4 + pathBytes.length;
        int argLen = (argBytes + ProtocolConst.ARG_UNIT_SIZE - 1) / ProtocolConst.ARG_UNIT_SIZE;

        // Phase 1: 探测文件大小 (packSize=4, packIdx=1)
        byte[] probeArg = new byte[argBytes];
        LittleEndianByteBufUtils.writeUShortLE(probeArg, 0, 4);
        LittleEndianByteBufUtils.writeUShortLE(probeArg, 2, 1);
        System.arraycopy(pathBytes, 0, probeArg, 4, pathBytes.length);

        byte[] probeReq = PacketBuilder.create(MainCmd.READ, SubCmd.READ_PATHFILE)
                .destAddr(gg, uu).needReply().withCrc()
                .argWithLen(probeArg, argLen).buildBytes();
        PacketMessage probeReply = transport.sendAndReceive(probeReq);
        checkStatus(probeReply);

        long totalSize = extractTotalSize(probeReply.getArg(), true);
        if (totalSize <= 0) return new byte[0];
        if (verbose) log.debug("[FILE] 文件大小={}B", totalSize);

        // Phase 2: 分块下载 (packSize=DEFAULT_CHUNK_SIZE, packIdx=1..N)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int chunkSize = Math.min(packSize, ProtocolConst.DEFAULT_CHUNK_SIZE);
        if (chunkSize < 4) chunkSize = ProtocolConst.DEFAULT_CHUNK_SIZE;

        for (int idx = 1; bos.size() < totalSize; idx++) {
            byte[] arg = new byte[argBytes];
            LittleEndianByteBufUtils.writeUShortLE(arg, 0, chunkSize);
            LittleEndianByteBufUtils.writeUShortLE(arg, 2, idx);
            System.arraycopy(pathBytes, 0, arg, 4, pathBytes.length);

            byte[] req = PacketBuilder.create(MainCmd.READ, SubCmd.READ_PATHFILE)
                    .destAddr(gg, uu).needReply().withCrc()
                    .argWithLen(arg, argLen).buildBytes();
            PacketMessage reply = transport.sendAndReceive(req);
            checkStatus(reply);

            byte[] chunk = reply.getData();
            if (chunk == null || chunk.length == 0) break;
            bos.write(chunk);

            if (verbose && idx % 10 == 0)
                log.debug("[FILE] 续传 #{}  {}/{}B", idx, bos.size(), totalSize);
        }

        byte[] result = bos.toByteArray();
        if (result.length > totalSize && totalSize > 0) {
            result = Arrays.copyOf(result, (int) totalSize);
        }
        if (verbose) log.debug("[FILE] 完成 {}B{}", result.length,
                result.length != totalSize ? " (期望" + totalSize + "B)" : "");
        return result;
    }

    private byte[] readChunked(byte mainCmd, byte subCmd, byte[] arg, int argLen)
            throws IOException {
        byte[] req = PacketBuilder.create(mainCmd, subCmd)
                .destAddr(gg, uu).needReply().argWithLen(arg, argLen).buildBytes();

        PacketMessage reply = transport.sendAndReceive(req);
        checkStatus(reply);

        long totalSize = extractTotalSize(reply.getArg(), true);
        byte[] chunk = reply.getData();
        if (chunk == null || chunk.length == 0) return new byte[0];

        if (verbose) log.debug("[FILE] 总大小={}B  首包={}B", totalSize, chunk.length);

        if (totalSize <= chunk.length) {
            if (chunk.length > totalSize && totalSize > 0) {
                return Arrays.copyOf(chunk, (int) totalSize);
            }
            return chunk;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(chunk);
        for (int idx = 2; bos.size() < totalSize; idx++) {
            updatePackIndex(arg, subCmd, idx);

            req = PacketBuilder.create(mainCmd, subCmd)
                    .destAddr(gg, uu).needReply().argWithLen(arg, argLen).buildBytes();
            reply = transport.sendAndReceive(req);
            checkStatus(reply);
            chunk = reply.getData();
            if (chunk == null || chunk.length == 0) break;
            bos.write(chunk);
            if (verbose && idx % 10 == 0) {
                log.debug("[FILE] 续传 #{}  {}/{}B", idx, bos.size(), totalSize);
            }
        }

        byte[] result = bos.toByteArray();
        if (result.length > totalSize && totalSize > 0) {
            result = Arrays.copyOf(result, (int) totalSize);
        }
        if (verbose) log.debug("[FILE] 完成 {}B{}", result.length,
                result.length != totalSize ? " (期望" + totalSize + "B)" : "");
        return result;
    }

    // ════════════════════════════════════════════════════
    // 文件列表 (0x070B) / 文件存在 (0x070E)
    // ════════════════════════════════════════════════════

    /**
     * 使用 0x0202 写入系统文件（如 SEQUENT.SYS 播放列表）。
     *
     * <p>Arg(24B): name(12B) + fileSize(4B) + chunkSize(2B) + totalPacks(2B) + packIndex(2B)</p>
     */
    public void writeSysFile(String fileName, byte[] fileData, int chunkSize) throws IOException {
        if (verbose) log.debug("[FILE] 写入系统文件 '{}' size={}B", fileName, fileData.length);

        byte[] name = padRight(fileName, 12);
        byte[] arg = new byte[24];
        System.arraycopy(name, 0, arg, 0, 12);
        LittleEndianByteBufUtils.writeUIntLE(arg, 12, fileData.length);
        LittleEndianByteBufUtils.writeUShortLE(arg, 16, chunkSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 18, 1);
        LittleEndianByteBufUtils.writeUShortLE(arg, 20, 1);

        byte[] req = PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_SYSFILE)
                .destAddr(gg, uu).needReply().withCrc()
                .argWithLen(arg, 6).data(fileData).buildBytes();

        if (verbose) {
            log.debug("[FILE] 发送报文 {}B: {}", req.length,
                    LittleEndianByteBufUtils.toHex(req, 0, Math.min(64, req.length)));
            log.debug("[FILE]   arg={}", LittleEndianByteBufUtils.toHex(arg));
            log.debug("[FILE]   data头={}",
                    LittleEndianByteBufUtils.toHex(fileData, 0, Math.min(32, fileData.length)));
        }

        PacketMessage reply = transport.sendAndReceive(req);
        if (verbose) {
            log.debug("[FILE] 回送: flag={} isStatus={} code=0x{}",
                    reply.getHeader().getFlag(), reply.isStatusReply(),
                    Integer.toHexString(reply.getStatusCode() & 0xFFFF));
        }
        checkWriteStatus(reply);
        if (verbose) log.debug("[FILE] 系统文件写入完成");
    }

    /**
     * 使用 0x0208 按路径写入文件到设备。
     *
     * @param filePath  目标路径（如 "D:\\T\\default.nmg"）
     * @param fileData  文件数据
     * @param chunkSize 单包大小
     */
    public void writePathFile(String filePath, byte[] fileData, int chunkSize) throws IOException {
        int totalPacks = (fileData.length + chunkSize - 1) / chunkSize;
        if (verbose) log.debug("[FILE] 写入路径 '{}' size={}B packs={}", filePath, fileData.length, totalPacks);

        byte[] pathBytes = (filePath + "\0").getBytes(ProtocolConstant.GB18030);
        int argBytes = 10 + pathBytes.length;
        int argLen = (argBytes + ProtocolConst.ARG_UNIT_SIZE - 1) / ProtocolConst.ARG_UNIT_SIZE;

        for (int idx = 1; idx <= totalPacks; idx++) {
            int off = (idx - 1) * chunkSize;
            int len = Math.min(chunkSize, fileData.length - off);
            byte[] chunk = Arrays.copyOfRange(fileData, off, off + len);

            byte[] arg = new byte[argLen * ProtocolConst.ARG_UNIT_SIZE];
            LittleEndianByteBufUtils.writeUIntLE(arg, 0, fileData.length);
            LittleEndianByteBufUtils.writeUShortLE(arg, 4, chunkSize);
            LittleEndianByteBufUtils.writeUShortLE(arg, 6, totalPacks);
            LittleEndianByteBufUtils.writeUShortLE(arg, 8, idx);
            System.arraycopy(pathBytes, 0, arg, 10, pathBytes.length);

            byte[] req = PacketBuilder.create(MainCmd.WRITE,
                            SubCmd.WRITE_PATHFILE)
                    .destAddr(gg, uu).needReply().withCrc()
                    .argWithLen(arg, argLen).data(chunk).buildBytes();

            PacketMessage reply = transport.sendAndReceive(req);
            checkWriteStatus(reply);

            if (verbose && (idx % 10 == 0 || idx == totalPacks)) {
                log.debug("[FILE] 写入 #{}/{}  offset={} len={}", idx, totalPacks, off, len);
            }
        }
        if (verbose) log.debug("[FILE] 路径写入完成");
    }

    // ════════════════════════════════════════════════════
    // 字库文件读写 (0x0103 / 0x0203)
    // ════════════════════════════════════════════════════

    /**
     * 使用 0x070B 列出指定文件夹下的文件
     */
    public String listDirectory(String path) throws IOException {
        byte[] pathBytes = (path + "\0").getBytes(ProtocolConstant.GB18030);
        int argLen = (pathBytes.length + 3) / ProtocolConst.ARG_UNIT_SIZE;
        byte[] arg = new byte[argLen * ProtocolConst.ARG_UNIT_SIZE];
        System.arraycopy(pathBytes, 0, arg, 0, pathBytes.length);

        byte[] req = PacketBuilder.create(MainCmd.FILE_CTL,
                        SubCmd.FILE_DIR)
                .destAddr(gg, uu).needReply().withCrc()
                .argWithLen(arg, argLen).buildBytes();

        PacketMessage reply = transport.sendAndReceive(req);
        checkStatus(reply);
        byte[] data = reply.getData();
        return data != null ? new String(data, ProtocolConstant.GB18030) : "";
    }

    /**
     * 使用 0x070E 检查指定路径文件是否存在
     */
    public boolean fileExists(String path) throws IOException {
        byte[] pathBytes = (path + "\0").getBytes(ProtocolConstant.GB18030);
        int argLen = (pathBytes.length + 3) / ProtocolConst.ARG_UNIT_SIZE;
        byte[] arg = new byte[argLen * ProtocolConst.ARG_UNIT_SIZE];
        System.arraycopy(pathBytes, 0, arg, 0, pathBytes.length);

        byte[] req = PacketBuilder.create(MainCmd.FILE_CTL,
                        SubCmd.FILE_EXISTS)
                .destAddr(gg, uu).needReply().withCrc()
                .argWithLen(arg, argLen).buildBytes();

        PacketMessage reply = transport.sendAndReceive(req);
        return !reply.isStatusReply() || StatusCode.isOk(reply.getStatusCode());
    }

    /**
     * 使用 0x0706 按路径删除文件。
     */
    public void deleteFile(String path) throws IOException {
        if (verbose) log.debug("[FILE] 删除 '{}' ...", path);
        byte[] pathBytes = (path + "\0").getBytes(ProtocolConstant.GB18030);
        int argLen = (pathBytes.length + 3) / ProtocolConst.ARG_UNIT_SIZE;
        byte[] arg = new byte[argLen * ProtocolConst.ARG_UNIT_SIZE];
        System.arraycopy(pathBytes, 0, arg, 0, pathBytes.length);

        byte[] req = PacketBuilder.create(MainCmd.FILE_CTL, SubCmd.FILE_DELETE)
                .destAddr(gg, uu).needReply().withCrc()
                .argWithLen(arg, argLen).buildBytes();

        PacketMessage reply = transport.sendAndReceive(req);
        checkStatus(reply);
        if (verbose) log.debug("[FILE] 删除完成: {}", path);
    }

    /**
     * 清除播放列表 — 写空 SEQUENT.SYS 并重载。
     */
    public void clearPlaylist() throws IOException {
        if (verbose) log.debug("[FILE] 清除播放列表 ...");
        SequentSysHelper.clear(this);
        if (verbose) log.debug("[FILE] 播放列表已清除");
    }

    /**
     * 使用 0x0103 读取字库文件。
     *
     * @param fileName 字库文件名（如 "SonTi16.FNT" 或 "FONTLIST.LST"）
     */
    public byte[] readFontFile(String fileName) throws IOException {
        if (verbose) log.debug("[FILE] 读取字库 '{}' ...", fileName);

        byte[] nameBytes = padRight(fileName, 12);

        // Phase 1: 探测文件大小 (packSize=4, packIdx=1)
        byte[] probeArg = new byte[16];
        System.arraycopy(nameBytes, 0, probeArg, 0, 12);
        LittleEndianByteBufUtils.writeUShortLE(probeArg, 12, 4);
        LittleEndianByteBufUtils.writeUShortLE(probeArg, 14, 1);

        byte[] probeReq = PacketBuilder.create(MainCmd.READ,
                        SubCmd.READ_FONTFILE)
                .destAddr(gg, uu).needReply()
                .argWithLen(probeArg, 4).buildBytes();

        PacketMessage probeReply = transport.sendAndReceive(probeReq);
        checkStatus(probeReply);

        long totalSize = extractTotalSize(probeReply.getArg(), true);
        if (totalSize <= 0) return new byte[0];
        if (verbose) log.debug("[FILE] 字库文件大小={}B", totalSize);

        // Phase 2: 分块下载
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int chunkSize = Math.min(packSize, ProtocolConst.DEFAULT_CHUNK_SIZE);
        if (chunkSize < 4) chunkSize = ProtocolConst.DEFAULT_CHUNK_SIZE;

        for (int idx = 1; bos.size() < totalSize; idx++) {
            byte[] arg = new byte[16];
            System.arraycopy(nameBytes, 0, arg, 0, 12);
            LittleEndianByteBufUtils.writeUShortLE(arg, 12, chunkSize);
            LittleEndianByteBufUtils.writeUShortLE(arg, 14, idx);

            byte[] req = PacketBuilder.create(MainCmd.READ,
                            SubCmd.READ_FONTFILE)
                    .destAddr(gg, uu).needReply()
                    .argWithLen(arg, 4).buildBytes();

            PacketMessage reply = transport.sendAndReceive(req);
            checkStatus(reply);

            byte[] chunk = reply.getData();
            if (chunk == null || chunk.length == 0) break;
            bos.write(chunk);

            if (verbose && idx % 10 == 0)
                log.debug("[FILE] 续传 #{}  {}/{}B", idx, bos.size(), totalSize);
        }

        byte[] result = bos.toByteArray();
        if (result.length > totalSize) {
            result = Arrays.copyOf(result, (int) totalSize);
        }
        if (verbose) log.debug("[FILE] 字库读取完成 {}B", result.length);
        return result;
    }

    /**
     * 使用 0x0103 读取 FONTLIST.LST 并解析为 FontListFile。
     */
    public FontListFile readFontList() throws IOException {
        byte[] data = readFontFile(SysFileName.FONTLIST_LST);
        if (data.length == 0) throw new IOException(SysFileName.FONTLIST_LST + " 为空或不存在");
        return FontListFile.parse(data);
    }

    /**
     * 使用 0x0203 写入单个字库文件。
     *
     * @param fileName  字库文件名
     * @param fontData  字库文件数据
     * @param chunkSize 单包数据大小
     */
    public void writeFontFile(String fileName, byte[] fontData, int chunkSize)
            throws IOException {
        int totalPacks = (fontData.length + chunkSize - 1) / chunkSize;
        if (verbose) log.debug("[FILE] 写入字库 '{}' size={}B packs={}", fileName, fontData.length, totalPacks);

        byte[] nameBytes = padRight(fileName, 12);

        for (int idx = 1; idx <= totalPacks; idx++) {
            int off = (idx - 1) * chunkSize;
            int len = Math.min(chunkSize, fontData.length - off);
            byte[] chunk = Arrays.copyOfRange(fontData, off, off + len);

            byte[] arg = buildFontWriteArg(nameBytes, fontData.length, chunkSize, totalPacks, idx);
            byte[] req = PacketBuilder.create(MainCmd.WRITE,
                            SubCmd.WRITE_FONTFILE)
                    .destAddr(gg, uu).needReply()
                    .argWithLen(arg, 6).data(chunk).buildBytes();

            PacketMessage reply = transport.sendAndReceive(req);
            checkWriteStatus(reply);

            if (verbose && (idx % 10 == 0 || idx == totalPacks)) {
                log.debug("[FILE] 写入 #{}/{}  offset={} len={}", idx, totalPacks, off, len);
            }
        }
        if (verbose) log.debug("[FILE] 字库写入完成");
    }

    /**
     * 使用 0x0203 写入字库文件（默认 768B 分块）
     */
    public void writeFontFile(String fileName, byte[] fontData) throws IOException {
        writeFontFile(fileName, fontData, ProtocolConst.DEFAULT_CHUNK_SIZE);
    }

    // ════════════════════════════════════════════════════
    // 播放控制
    // ════════════════════════════════════════════════════

    /**
     * 使用 0x0203 写入 FONTLIST.LST。
     */
    public void writeFontList(FontListFile list)
            throws IOException {
        byte[] data = list.toBytes();
        if (verbose) log.debug("[FILE] 写入 FONTLIST.LST ({} 字体, {}B)", list.count(), data.length);
        writeFontFile(SysFileName.FONTLIST_LST, data, data.length);
    }

    /**
     * 发送 0x0601 REPLAY_LIST 命令，通知设备重新加载播放列表
     */
    public void replayPlaylist() throws IOException {
        if (verbose) log.debug("[FILE] 发送 REPLAY_LIST ...");
        byte[] req = PacketBuilder.create(MainCmd.DISPLAY,
                        SubCmd.DISP_REPLAY_LIST)
                .destAddr(gg, uu).needReply().withCrc()
                .argWithLen(new byte[4], 1).buildBytes();
        PacketMessage reply = transport.sendAndReceive(req);
        checkWriteStatus(reply);
        if (verbose) log.debug("[FILE] 播放列表已重载");
    }

}
