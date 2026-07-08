package com.gateway.device.protocol.base.jetfileii.standard.sys;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;

import java.io.IOException;
import java.util.List;

/**
 * SEQUENT.SYS 播放列表构建工具。
 *
 * <p>三个公开方法覆盖全部场景：
 * <ul>
 *   <li>{@link #write(FileTransfer, List)} — 通用写入，1…N 条路径</li>
 *   <li>{@link #writeText(FileTransfer, String, int)} — TEXT 条目，含行数元数据</li>
 *   <li>{@link #clear(FileTransfer)} — 清空播放列表</li>
 * </ul>
 */
public final class SequentSysHelper {

    private static final String SYS_FILE_NAME = SysFileName.SEQUENT_SYS;

    private SequentSysHelper() {
    }

    /**
     * 写入播放列表 — 单曲或多曲通用。
     *
     * <p>paths.size()==1 → flags=0x0101（单曲当前播放），否则 flags=0x0001（多曲列表）。
     * PICTURE 条目自动标记 {@code pictureEntry()}。</p>
     */
    public static void write(FileTransfer ft, List<String> paths) throws IOException {
        boolean single = paths.size() == 1;
        SequentSysFile sf = new SequentSysFile();
        sf.getHeader().setCurrentIndex((short) paths.size());
        sf.getHeader().setField((short) SequentSysFile.FIELD_EXPANDED);
        sf.getHeader().setFlags((short) (single ? 0x0101 : 0x0001));
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            SequentSysFile.Entry e = sf.addEntry(path, i == paths.size() - 1);
            if (isPicturePath(path)) {
                e.pictureEntry();
            }
        }
        ft.writeSysFile(SYS_FILE_NAME, sf.toBytes(), ProtocolConst.DEFAULT_CHUNK_SIZE);
        ft.replayPlaylist();
    }

    /**
     * 写入 TEXT 单曲播放列表（含行数元数据，flags=0x0101）。
     */
    public static void writeText(FileTransfer ft, String filePath, int lineCount) throws IOException {
        SequentSysFile sf = new SequentSysFile();
        sf.getHeader().setCurrentIndex((short) 1);
        sf.getHeader().setField((short) SequentSysFile.FIELD_EXPANDED);
        sf.getHeader().setFlags((short) 0x0101);
        SequentSysFile.Entry e = sf.addEntry(filePath, true);
        if (lineCount > 0) {
            e.textEntry(lineCount);
        } else if (isPicturePath(filePath)) {
            e.pictureEntry();
        }
        ft.writeSysFile(SYS_FILE_NAME, sf.toBytes(), ProtocolConst.DEFAULT_CHUNK_SIZE);
        ft.replayPlaylist();
    }

    /**
     * 清除播放列表（空列表 + 重载）
     */
    public static void clear(FileTransfer ft) throws IOException {
        SequentSysFile sf = new SequentSysFile();
        sf.getHeader().setCurrentIndex((short) 0);
        sf.getHeader().setField((short) SequentSysFile.FIELD_EXPANDED);
        ft.writeSysFile(SYS_FILE_NAME, sf.toBytes(), ProtocolConst.DEFAULT_CHUNK_SIZE);
        ft.replayPlaylist();
    }

    /**
     * 通过路径前缀判断是否为 PICTURE 条目（{@code :\P\}），不硬编码扩展名。
     */
    private static boolean isPicturePath(String path) {
        if (path == null || path.length() < 4) return false;
        return path.charAt(1) == ':'
                && path.charAt(2) == '\\'
                && path.charAt(3) == FileType.PICTURE.getCode();
    }
}
