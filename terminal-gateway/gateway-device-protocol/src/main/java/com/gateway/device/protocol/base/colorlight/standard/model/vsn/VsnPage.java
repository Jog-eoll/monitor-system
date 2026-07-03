package com.gateway.device.protocol.base.colorlight.standard.model.vsn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.BgAudio;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.BgFile;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.LoopType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VsnPage {
    /**
     * FIXME[类型存疑] 字符串|数字
     */
    private LoopType loopType;
    private Long appointDuration;
    private String bgColor;
    private BgFile bgFile;
    private List<BgAudio> bgAudios;
    private List<VsnRegion> regions;
    /**
     * VSN 部分文件中 isBucketProgram 出现在 Page 层级
     */
    @JsonProperty("isBucketProgram")
    private Integer isBucketProgram;
}
