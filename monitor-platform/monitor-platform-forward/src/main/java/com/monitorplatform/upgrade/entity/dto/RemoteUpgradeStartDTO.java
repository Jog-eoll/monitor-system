package com.monitorplatform.upgrade.entity.dto;

import lombok.Data;

/**
 * 远程升级启动请求。
 */
@Data
public class RemoteUpgradeStartDTO {

    /**
     * 等待 MQTT 最终回执。批量升级默认 false，避免接口长时间阻塞。
     */
    private Boolean waitForResult = false;
}
