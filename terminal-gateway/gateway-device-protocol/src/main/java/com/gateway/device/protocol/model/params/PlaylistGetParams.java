package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 播放列表查询参数 — PLAYLIST_GET。
 *
 * <p>JetFileII: sysFile 系统文件名（如 SEQUENT.SYS），默认读取当前播放文件。</p>
 */
@Data
@Builder
public class PlaylistGetParams implements CommandParams {

    /**
     * 系统文件名（如 SEQUENT.SYS），为空时读取当前播放文件
     */
    private String sysFile;
}
