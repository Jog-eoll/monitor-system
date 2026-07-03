package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 播放列表设置参数 — PLAYLIST_SET。
 *
 * <p>Nova: 使用 identifier 或 name 匹配已部署节目并激活播放（优先 identifier）
 * <br>JetFileII: 使用 paths(文件路径列表) 设定播放列表，checkExistence 控制是否预检</p>
 */
@Data
@Builder
public class PlaylistSetParams implements CommandParams {

    /**
     * 目标节目 identifier（Nova: 直接匹配已部署节目 ID，优先于 name）
     */
    private String identifier;

    /**
     * 目标节目名称（Nova: identifier 为空时回退到名称匹配）
     */
    private String name;

    /**
     * 播放列表文件路径（JetFileII，空列表=清除）
     */
    private List<String> paths;

    /**
     * 设定前是否校验文件存在（JetFileII）
     */
    @Builder.Default
    private boolean checkExistence = false;
}
